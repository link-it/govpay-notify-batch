package it.govpay.notify.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.json.JsonMapper;

import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.service.GdeService;
import it.govpay.notify.batch.service.EnteApiService;

/**
 * Test dell'interazione HTTP di {@link EnteApiService} con l'API di
 * integrazione dell'Ente, esercitata end-to-end sul client OpenAPI generato
 * tramite {@link MockRestServiceServer}: il RestTemplate restituito dal
 * ConnettoreService e' reale, quindi il test attraversa serializzazione JSON,
 * costruzione dell'URL e mapping degli status code cosi' come avviene in
 * produzione.
 * <p>
 * Complementare a {@link EnteApiServiceTest}, che copre invece i casi di
 * configurazione non idonea, in cui nessuna richiesta HTTP viene inviata.
 */
@DisplayName("EnteApiService - interazione HTTP con l'Ente")
class EnteApiServiceHttpTest {

    private static final String COD_APP = "APP_TEST";
    private static final String COD_CONN = "CONN_TEST";
    private static final String BASE_URL = "https://ente.example.com";
    private static final String NOTIFY_URL = BASE_URL + "/rendicontazioni";

    private ConnettoreService connettoreService;
    private ApplicazioneRepository applicazioneRepository;
    private GdeService gdeService;
    private MockRestServiceServer mockServer;
    private EnteApiService service;
    private RtNotifyContext rtInfo;

    @BeforeEach
    void setUp() {
        connettoreService = mock(ConnettoreService.class);
        applicazioneRepository = mock(ApplicazioneRepository.class);
        gdeService = mock(GdeService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        ApplicazioneEntity app = new ApplicazioneEntity();
        app.setCodApplicazione(COD_APP);
        app.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(app));
        when(connettoreService.getConnettore(COD_CONN))
                .thenReturn(Connettore.builder().idConnettore(COD_CONN).url(BASE_URL).build());
        when(connettoreService.getConnettoreAsMap(COD_CONN)).thenReturn(Map.of("VERSIONE", "REST_2"));
        when(connettoreService.getRestTemplate(COD_CONN)).thenReturn(restTemplate);

        EnteApiClientConfig config = new EnteApiClientConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");

        service = new EnteApiService(connettoreService, applicazioneRepository, config,
                gdeService, transactionManager);

        rtInfo = RtNotifyContext.builder()
                .rtId(1L)
                .codApplicazione(COD_APP)
                .taxCode("12345678901")
                .iuv("01234567890123456")
                .iur("IUR123")
                .indice(1)
                .importo(BigDecimal.TEN)
                .esito(0)
                .data(OffsetDateTime.of(2025, 3, 12, 10, 0, 0, 0, ZoneOffset.UTC))
                .idFlusso("FLUSSO-1")
                .trn("TRN-1")
                .build();
    }

    private CompletableFuture<HttpStatusCode> notifica() {
        return notifica(rtInfo);
    }

    private CompletableFuture<HttpStatusCode> notifica(RtNotifyContext context) {
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();
        esito = service.notifyRendicontazione(context, future);
        return future;
    }

    /** Valore di ritorno dell'ultima notify(), che e' il messaggio d'errore o null se OK. */
    private String esito;

    @Test
    @DisplayName("200 OK -> nessun messaggio d'errore, evento OK al GDE, payload conforme al context")
    void happyPathSendsPayloadAndTracksOkEvent() {
        mockServer.expect(requestTo(NOTIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.idDominio").value("12345678901"))
                .andExpect(jsonPath("$.iuv").value("01234567890123456"))
                .andExpect(jsonPath("$.iur").value("IUR123"))
                .andExpect(jsonPath("$.indice").value(1))
                .andExpect(jsonPath("$.esito").value("ESEGUITO"))
                .andExpect(jsonPath("$.idFlusso").value("FLUSSO-1"))
                .andRespond(withSuccess());

        CompletableFuture<HttpStatusCode> future = notifica();

        assertNull(esito);
        assertEquals(HttpStatus.OK, future.join());
        mockServer.verify();
        verify(gdeService).saveNotifyRndOk(eq(rtInfo), any(), any(), any(), any(), eq(BASE_URL));
        verify(gdeService, never()).saveNotifyRndKo(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest(name = "HTTP {0} -> \"{1}\"")
    @CsvSource({
            "400, Notifica errata",
            "401, Non autorizzato",
            "403, Accesso negato",
            "500, Internal error"
    })
    @DisplayName("errore HTTP -> messaggio dedicato, future completato con lo stesso stato ed evento KO al GDE")
    void httpErrorsAreMappedToMessagesAndKoEvents(int status, String expectedMessage) {
        mockServer.expect(requestTo(NOTIFY_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        CompletableFuture<HttpStatusCode> future = notifica();

        assertEquals(expectedMessage, esito);
        assertEquals(HttpStatus.valueOf(status), future.join());
        mockServer.verify();
        verify(gdeService).saveNotifyRndKo(eq(rtInfo), any(), any(), any(), any(), any(), eq(BASE_URL));
        verify(gdeService, never()).saveNotifyRndOk(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest(name = "esito {0} -> {1}")
    @CsvSource({
            "0, ESEGUITO",
            "3, REVOCATO",
            "4, ESEGUITO_STANDIN",
            "8, ESEGUITO_STANDIN_SENZA_RPT",
            "9, ESEGUITO_SENZA_RPT"
    })
    @DisplayName("i codici esito del DB sono mappati sull'enum dell'API EC")
    void esitoCodesAreMappedToApiEnum(int codice, String atteso) {
        mockServer.expect(requestTo(NOTIFY_URL))
                .andExpect(jsonPath("$.esito").value(atteso))
                .andRespond(withSuccess());

        notifica(rtInfo.toBuilder().esito(codice).build());

        assertNull(esito);
        mockServer.verify();
    }

    @Test
    @DisplayName("codice esito non riconosciuto -> configurazione non idonea, nessuna chiamata all'Ente")
    void unknownEsitoIsRejectedBeforeAnyHttpCall() {
        CompletableFuture<HttpStatusCode> future = notifica(rtInfo.toBuilder().esito(7).build());

        assertTrue(esito.startsWith("Configurazione non idonea"));
        assertTrue(esito.contains("7"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
        // Nessuna expectation dichiarata: verify() fallirebbe se fosse partita una richiesta.
        mockServer.verify();
        verify(gdeService, never()).saveNotifyRndKo(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("l'istanza API e' creata una sola volta per connettore (cache) e ricreata dopo clearCache")
    void apiInstanceIsCachedPerConnector() {
        mockServer.expect(ExpectedCount.twice(), requestTo(NOTIFY_URL)).andRespond(withSuccess());

        notifica();
        notifica();

        verify(connettoreService, times(1)).getRestTemplate(COD_CONN);

        service.clearCache();
        mockServer.reset();
        mockServer.expect(requestTo(NOTIFY_URL)).andRespond(withSuccess());
        notifica();

        verify(connettoreService, times(2)).getRestTemplate(COD_CONN);
        verify(connettoreService).clearCache();
    }


    @Test
    @DisplayName("serializzazione del payload fallita -> record BAD_REQUEST ed evento KO, senza propagare l'eccezione")
    void jsonSerializationFailureIsContained() {
        // La serializzazione avviene prima dell'invio: se fallisce (es. limiti di stream
        // di Jackson 3) il record deve andare in errore e il batch proseguire, non
        // interrompersi con una JacksonException risalita fino allo step.
        JsonMapper mapperKo = mock(JsonMapper.class);
        when(mapperKo.writeValueAsString(any()))
                .thenThrow(new StreamConstraintsException("documento troppo profondo"));
        EnteApiClientConfig configKo = mock(EnteApiClientConfig.class);
        when(configKo.createEnteObjectMapper()).thenReturn(mapperKo);

        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        EnteApiService serviceKo = new EnteApiService(connettoreService, applicazioneRepository,
                configKo, gdeService, tm);

        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();
        String messaggio = serviceKo.notifyRendicontazione(rtInfo, future);

        assertEquals("Fail to convert object to json", messaggio);
        assertEquals(HttpStatus.BAD_REQUEST, future.join());
        verify(gdeService).saveNotifyRndKo(eq(rtInfo), any(), any(), isNull(), any(), any(), eq(BASE_URL));
    }
}
