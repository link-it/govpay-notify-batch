package it.govpay.notify.batch.rt.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.json.JsonMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.ec.client.api.NotificaRicevuteApi;
import it.govpay.ec.client.api.impl.ApiClient;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import lombok.extern.slf4j.Slf4j;

/**
 * Service che spedisce le ricevute di pagamento (RT) all'API di integrazione
 * dell'Ente, usando il client OpenAPI generato (v2 EC API:
 * {@code PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}}).
 * <p>
 * Versione del connettore letta da {@link ConnettoreService#getConnettoreAsMap}
 * come workaround in attesa di un getter strong-typed su
 * {@code Connettore} (vedi {@code docs/issues/govpay-common-versione-connettore.md}).
 * <p>
 * Solo la versione {@link Costanti#VERSIONE_API_EC_SUPPORTATA} e' supportata
 * dalla PR corrente: altre versioni sollevano {@link UnsupportedOperationException},
 * trattata dal writer come errore di spedizione standard (updateDaSpedire).
 */
@Slf4j
@Service
public class EnteRicevutaApiService {

    private final ConnettoreService connettoreService;
    private final ApplicazioneRepository applicazioneRepository;
    private final RicevutaV2Mapper ricevutaV2Mapper;
    private final JsonMapper clientObjectMapper;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    private final ConcurrentHashMap<String, NotificaRicevuteApi> apiCache = new ConcurrentHashMap<>();

    public EnteRicevutaApiService(
            ConnettoreService connettoreService,
            ApplicazioneRepository applicazioneRepository,
            EnteApiClientConfig enteApiClientConfig,
            RicevutaV2Mapper ricevutaV2Mapper,
            @Value("${govpay.batch.rt-send.http.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${govpay.batch.rt-send.http.read-timeout-ms:30000}") long readTimeoutMs) {
        this.connettoreService = connettoreService;
        this.applicazioneRepository = applicazioneRepository;
        this.ricevutaV2Mapper = ricevutaV2Mapper;
        this.clientObjectMapper = enteApiClientConfig.createEnteObjectMapper();
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
    }

    /**
     * Spedisce la ricevuta all'API di integrazione dell'Ente.
     *
     * @throws IllegalStateException se l'applicazione o il connettore non sono configurati
     * @throws UnsupportedOperationException se la versione del connettore non e' supportata
     */
    public ResponseEntity<Void> sendRicevuta(Notifica notifica, Rpt rpt) {
        String codApplicazione = notifica.getApplicazione().getCodApplicazione();
        String codConnettore = resolveConnectorCode(codApplicazione);
        verificaVersioneSupportata(codConnettore);

        NotificaRicevuteApi api = getOrCreateApi(codConnettore);
        Ricevuta payload = ricevutaV2Mapper.toRicevuta(rpt);

        log.debug("Spedizione ricevuta RT a {} (codDominio={}, iuv={}, ccp={})",
                codApplicazione, rpt.getCodDominio(), rpt.getIuv(), rpt.getCcp());

        return api.notificaRicevutaWithHttpInfo(
                rpt.getCodDominio(),
                rpt.getIuv(),
                rpt.getCcp(),
                rpt.getCodSessione(),
                rpt.getCodSessionePortale(),
                rpt.getCodCarrello(),
                payload);
    }

    /**
     * Svuota la cache delle istanze API. Da invocare se la configurazione di un
     * connettore cambia a runtime.
     */
    public void clearCache() {
        apiCache.clear();
        connettoreService.clearCache();
        log.info("Cache connettori RT svuotata");
    }

    private String resolveConnectorCode(String codApplicazione) {
        Optional<ApplicazioneEntity> appOpt = applicazioneRepository.findByCodApplicazione(codApplicazione);
        ApplicazioneEntity app = appOpt.orElseThrow(() -> new IllegalStateException(
                "Nessuna applicazione trovata per il codice applicazione: " + codApplicazione));

        String codConnettore = app.getCodConnettoreIntegrazione();
        if (codConnettore == null || codConnettore.isBlank()) {
            throw new IllegalStateException(
                    "Connettore di integrazione non configurato per l'applicazione " + codApplicazione);
        }
        return codConnettore;
    }

    /**
     * Verifica che il connettore sia configurato per la versione supportata.
     * <p>
     * Workaround temporaneo: la versione e' letta da
     * {@link ConnettoreService#getConnettoreAsMap}. Dopo l'evoluzione di
     * govpay-common (issue dedicata) sostituire con
     * {@code connettoreService.getConnettore(code).getVersione()}.
     */
    private void verificaVersioneSupportata(String codConnettore) {
        Map<String, String> props = connettoreService.getConnettoreAsMap(codConnettore);
        if (props == null || props.isEmpty()) {
            throw new IllegalStateException(
                    "Connettore non configurato: " + codConnettore);
        }
        String versione = props.get(Costanti.CONNETTORE_PROPRIETA_VERSIONE);
        if (!Costanti.VERSIONE_API_EC_SUPPORTATA.equals(versione)) {
            throw new UnsupportedOperationException(
                    "Versione EC API non supportata sul connettore " + codConnettore
                            + ": atteso " + Costanti.VERSIONE_API_EC_SUPPORTATA
                            + ", trovato " + versione);
        }
    }

    private NotificaRicevuteApi getOrCreateApi(String codConnettore) {
        return apiCache.computeIfAbsent(codConnettore, code -> {
            RestTemplate restTemplate = connettoreService.getRestTemplate(code);
            applicaTimeout(restTemplate);

            JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(clientObjectMapper);
            restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
            restTemplate.getMessageConverters().add(0, converter);

            Connettore connettore = connettoreService.getConnettore(code);
            ApiClient apiClient = new ApiClient(restTemplate);
            apiClient.setBasePath(connettore.getUrl());

            log.info("Creata istanza NotificaRicevuteApi per connettore {} (URL: {})", code, connettore.getUrl());
            return new NotificaRicevuteApi(apiClient);
        });
    }

    /**
     * Applica i timeout da properties al {@link RestTemplate}. Per non perdere
     * l'eventuale configurazione SSL/mTLS della {@link ClientHttpRequestFactory}
     * costruita da govpay-common, si interviene solo su factory note
     * (oggi solo il fallback non-SSL {@link SimpleClientHttpRequestFactory}).
     * Negli altri casi si lascia invariato e si logga: i timeout vanno
     * eventualmente configurati upstream (es. proprieta' del connettore).
     */
    private void applicaTimeout(RestTemplate restTemplate) {
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        if (factory instanceof SimpleClientHttpRequestFactory simple) {
            simple.setConnectTimeout((int) connectTimeout.toMillis());
            simple.setReadTimeout((int) readTimeout.toMillis());
        } else {
            log.debug("Timeout RT non applicati: ClientHttpRequestFactory non gestita ({})",
                    factory.getClass().getName());
        }
    }
}
