package it.govpay.notify.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.ObjectMapper;

import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.common.gde.GdeUtils;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.mapper.EventoRtMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending RT notify events to the GDE microservice.
 * <p>
 * Extends {@link AbstractGdeService} from govpay-common for RestTemplate-based
 * async event sending via ConfigurazioneService.
 * <p>
 * Events include:
 * - rendicontazioni: Sending rendicontazione info to ente
 */
@Slf4j
@Service
public class GdeService extends AbstractGdeService {
    private final EventoRtMapper eventoRtMapper;
    private final ConfigurazioneService configurazioneService;

    @Value("${govpay.url}")
    private String govpayUrl;

    public GdeService(ObjectMapper objectMapper,
                      @Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
                      ConfigurazioneService configurazioneService,
                      EventoRtMapper eventoRtMapper) {
        super(objectMapper, asyncHttpExecutor, configurazioneService);
        this.eventoRtMapper = eventoRtMapper;
        this.configurazioneService = configurazioneService;
    }

    @Override
    protected String getGdeEndpoint() {
        return configurazioneService.getServizioGDE().getUrl() + "/eventi";
    }

    @Override
    protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
        throw new UnsupportedOperationException(
                "GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
    }

    @Override
    protected GdeInterfaccia getConfigurazioneComponente(ComponenteEvento componente, Giornale giornale) {
        throw new UnsupportedOperationException(
                "GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
    }

    /**
     * Sends an event to GDE asynchronously using the inherited async executor
     * and RestTemplate from ConfigurazioneService.
     *
     * @param nuovoEvento Event to send
     */
    public void sendEventAsync(NuovoEvento nuovoEvento) {
        if (!isAbilitato()) {
            log.debug("Connettore GDE disabilitato, evento {} non inviato", nuovoEvento.getTipoEvento());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                getGdeRestTemplate().postForEntity(getGdeEndpoint(), nuovoEvento, Void.class);
                log.debug("Evento {} inviato con successo al GDE", nuovoEvento.getTipoEvento());
            } catch (Exception ex) {
                log.warn("Impossibile inviare evento {} al GDE (il batch continua normalmente): {}",
                        nuovoEvento.getTipoEvento(), ex.getMessage());
                log.debug("Dettaglio errore GDE:", ex);
            } finally {
                HttpDataHolder.clear();
            }
        }, this.asyncExecutor);
    }

    /**
     * Records a successful NOTIFY operation.
     *
     * @param rtInfo          rt notify information: Organization tax code, IUR, IUV
     * @param jsonRequest     json request in post
     * @param responseEntity  HTTP response
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param enteBaseUrl     base URL ente (from ConnettoreService)
     */
    public void saveNotifyRndOk(RtNotifyContext rtInfo, String jsonRequest, ResponseEntity<?> responseEntity,
                                OffsetDateTime dataStart, OffsetDateTime dataEnd, String enteBaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildRndUrl(enteBaseUrl, rtInfo);
        NuovoEvento nuovoEvento = eventoRtMapper.createEventoOk(
                rtInfo, Costanti.PATH_NOTIFY_RND, transactionId, dataStart, dataEnd);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders(), jsonRequest);
        eventoRtMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);

        sendEventAsync(nuovoEvento);
    }

    /**
     * Records a failed NOTIFY operation.
     *
     * @param rtInfo          rt notify information: Organization tax code, IUR, IUV
     * @param jsonRequest     json request in post
     * @param responseEntity  HTTP response
     * @param exception       the exception that occurred
     * @param dataStart       timestamp inizio operazione
     * @param dataEnd         timestamp fine operazione
     * @param enteBaseUrl     base URL ente (from ConnettoreService)
     */
    public void saveNotifyRndKo(RtNotifyContext rtInfo, String jsonRequest, ResponseEntity<?> responseEntity, RestClientException exception,
                                OffsetDateTime dataStart, OffsetDateTime dataEnd, String enteBaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildRndUrl(enteBaseUrl, rtInfo);
        NuovoEvento nuovoEvento = eventoRtMapper.createEventoKo(
                rtInfo, Costanti.PATH_NOTIFY_RND, transactionId, dataStart, dataEnd, null, exception);

        eventoRtMapper.setParametriRichiesta(nuovoEvento, url, "POST", GdeUtils.getCapturedRequestHeadersAsGdeHeaders(), jsonRequest);
        eventoRtMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        setResponsePayload(nuovoEvento, responseEntity, exception);

        sendEventAsync(nuovoEvento);
    }

    /**
     * Sets the response payload on the event using the common GdeUtils.extractResponsePayload().
     */
    private void setResponsePayload(NuovoEvento nuovoEvento, ResponseEntity<?> responseEntity,
                                    RestClientException exception) {
        if (nuovoEvento.getParametriRisposta() != null) {
            nuovoEvento.getParametriRisposta().setPayload(
                extractResponsePayload(responseEntity, exception));
        }
    }

    /**
     * Builds the URL for notify operations using GdeUtils.buildUrl().
     */
    private String buildRndUrl(String enteBaseUrl, RtNotifyContext rtInfo) {
        return GdeUtils.buildUrl(enteBaseUrl, Costanti.PATH_NOTIFY_RND, null, null);
    }
}
