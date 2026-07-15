package it.govpay.notify.batch.gde.mapper;

import it.govpay.gde.client.beans.*;
import it.govpay.notify.batch.dto.RtNotifyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for creating GDE events from FDR batch operations.
 * <p>
 * This mapper creates NuovoEvento objects to track RT notify operations
 */
@Slf4j
@Component
public class EventoRtMapper {

    @Value("${govpay.batch.cluster-id}")
    private String clusterId;

    /**
     * Creates DatiPagoPA object from rt notify info.
     *
     * @param rtInfo RtNotifyContext
     * @return DatiPagoPA with FDR-specific data
     */     
    private DatiPagoPA createDatiPagoPA(RtNotifyContext rtInfo) {
        DatiPagoPA datiPagoPA = new DatiPagoPA();
        datiPagoPA.setIdDominio(rtInfo.getTaxCode());
        return datiPagoPA;
    }   

    /**
     * Creates a base event.
     *
     * @param rtInfo           rt notify info
     * @param tipoEvento       Event type (e.g., GET_PUBLISHED_FLOWS, GET_FLOW_DETAILS)
     * @param transactionId    Unique transaction identifier
     * @param dataStart        Event start timestamp
     * @param dataEnd          Event end timestamp
     * @return NuovoEvento with base fields populated
     */
    public NuovoEvento createEvento(RtNotifyContext rtInfo, String tipoEvento, String transactionId,
                                    OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = new NuovoEvento();

        if (rtInfo != null) {
        	nuovoEvento.setIdDominio(rtInfo.getTaxCode());
        	nuovoEvento.setIuv(rtInfo.getIuv());
        	nuovoEvento.setCcp(rtInfo.getIur());
        	nuovoEvento.setDatiPagoPA(createDatiPagoPA(rtInfo));
        }

        // Set event metadata
        nuovoEvento.setCategoriaEvento(CategoriaEvento.INTERFACCIA);
        nuovoEvento.setClusterId(clusterId);
        nuovoEvento.setDataEvento(dataStart);
        // durataEvento richiede entrambi i timestamp; se uno manca lascio il campo nullo
        // invece di sollevare NPE. dataStart/dataEnd sono valorizzati da EnteApiService in
        // tutti i path noti, ma il guard difende da regressioni future.
        if (dataStart != null && dataEnd != null) {
            nuovoEvento.setDurataEvento(dataEnd.toInstant().toEpochMilli() - dataStart.toInstant().toEpochMilli());
        }
        nuovoEvento.setRuolo(RuoloEvento.CLIENT);
        nuovoEvento.setComponente(ComponenteEvento.API_ENTE);
        nuovoEvento.setTipoEvento(tipoEvento);
        nuovoEvento.setTransactionId(transactionId);

        return nuovoEvento;
    }

    /**
     * Creates an OK event for successful operations.
     *
     * @param rtInfo           rt notify info
     * @param tipoEvento       Event type
     * @param transactionId    Transaction ID
     * @param dataStart        Start timestamp
     * @param dataEnd          End timestamp
     * @return NuovoEvento with OK outcome
     */
    public NuovoEvento createEventoOk(RtNotifyContext rtInfo, String tipoEvento, String transactionId,
                                      OffsetDateTime dataStart, OffsetDateTime dataEnd) {
        NuovoEvento nuovoEvento = createEvento(rtInfo, tipoEvento, transactionId, dataStart, dataEnd);
        nuovoEvento.setEsito(EsitoEvento.OK);
        return nuovoEvento;
    }

    /**
     * Creates a KO/FAIL event for failed operations.
     *
     * @param rtInfo           rt notify info
     * @param tipoEvento       Event type
     * @param transactionId    Transaction ID
     * @param dataStart        Start timestamp
     * @param dataEnd          End timestamp
     * @param responseEntity   Response entity (if available)
     * @param exception        Exception (if any)
     * @return NuovoEvento with KO/FAIL outcome
     */
    public NuovoEvento createEventoKo(RtNotifyContext rtInfo, String tipoEvento, String transactionId,
                                      OffsetDateTime dataStart, OffsetDateTime dataEnd,
                                      ResponseEntity<?> responseEntity, RestClientException exception) {
        NuovoEvento nuovoEvento = createEvento(rtInfo, tipoEvento, transactionId, dataStart, dataEnd);
        extractExceptionInfo(responseEntity, exception, nuovoEvento);
        return nuovoEvento;
    }

    /**
     * Sets request details on the event.
     *
     * @param nuovoEvento      Event to update
     * @param urlOperazione    Operation URL
     * @param httpMethod       HTTP method (GET, POST, etc.)
     * @param headers          HTTP headers
     */
    public void setParametriRichiesta(NuovoEvento nuovoEvento, String urlOperazione,
                                      String httpMethod, List<Header> headers, String jsonBody) {
        DettaglioRichiesta dettaglioRichiesta = new DettaglioRichiesta();
        dettaglioRichiesta.setDataOraRichiesta(nuovoEvento.getDataEvento());
        dettaglioRichiesta.setMethod(httpMethod);
        dettaglioRichiesta.setUrl(urlOperazione);
        dettaglioRichiesta.setHeaders(headers);
        dettaglioRichiesta.setPayload(jsonBody);

        nuovoEvento.setParametriRichiesta(dettaglioRichiesta);
    }

    /**
     * Sets response details on the event.
     *
     * @param nuovoEvento      Event to update
     * @param dataEnd          Response timestamp
     * @param responseEntity   Response entity
     * @param exception        Exception (if any)
     */
    public void setParametriRisposta(NuovoEvento nuovoEvento, OffsetDateTime dataEnd,
                                     ResponseEntity<?> responseEntity, RestClientException exception) {
        DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();
        dettaglioRisposta.setDataOraRisposta(dataEnd);

        List<Header> headers = new ArrayList<>();

        if (responseEntity != null) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(responseEntity.getStatusCode().value()));

            HttpHeaders httpHeaders = responseEntity.getHeaders();
            httpHeaders.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    Header header = new Header();
                    header.setNome(key);
                    header.setValore(value.get(0));
                    headers.add(header);
                }
            });
        } else if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(httpStatusCodeException.getStatusCode().value()));

            HttpHeaders httpHeaders = httpStatusCodeException.getResponseHeaders();
            if (httpHeaders != null) {
                httpHeaders.forEach((key, value) -> {
                    if (!value.isEmpty()) {
                        Header header = new Header();
                        header.setNome(key);
                        header.setValore(value.get(0));
                        headers.add(header);
                    }
                });
            }
        } else {
            dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
        }

        dettaglioRisposta.setHeaders(headers);
        nuovoEvento.setParametriRisposta(dettaglioRisposta);
    }

    private void extractExceptionInfo(ResponseEntity<?> responseEntity, RestClientException exception,
                                      NuovoEvento nuovoEvento) {
        if (exception != null) {
            if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
                nuovoEvento.setDettaglioEsito(httpStatusCodeException.getResponseBodyAsString());
                nuovoEvento.setSottotipoEsito(httpStatusCodeException.getStatusCode().value() + "");

                if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
                    nuovoEvento.setEsito(EsitoEvento.FAIL);
                } else {
                    nuovoEvento.setEsito(EsitoEvento.KO);
                }
            } else {
                nuovoEvento.setDettaglioEsito(exception.getMessage());
                nuovoEvento.setSottotipoEsito("500");
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            }
        } else if (responseEntity != null) {
            nuovoEvento.setDettaglioEsito(HttpStatus.valueOf(responseEntity.getStatusCode().value()).getReasonPhrase());
            nuovoEvento.setSottotipoEsito("" + responseEntity.getStatusCode().value());

            if (responseEntity.getStatusCode().is5xxServerError()) {
                nuovoEvento.setEsito(EsitoEvento.FAIL);
            } else {
                nuovoEvento.setEsito(EsitoEvento.KO);
            }
        }
    }
}
