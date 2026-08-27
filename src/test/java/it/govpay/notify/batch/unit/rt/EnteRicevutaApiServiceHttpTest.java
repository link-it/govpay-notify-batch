package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.entity.Applicazione;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.rt.service.EnteRicevutaApiService;
import it.govpay.notify.batch.rt.service.RicevutaV2Mapper;

/**
 * Test della spedizione HTTP di {@link EnteRicevutaApiService} verso l'API di
 * integrazione dell'Ente, esercitata sul client OpenAPI generato tramite
 * {@link MockRestServiceServer}: verifica metodo, URL con i path parameter
 * dell'API v2 e il caching dell'istanza API per connettore.
 * <p>
 * Complementare a {@link EnteRicevutaApiServiceTest}, che copre le validazioni
 * di precondizione (applicazione, connettore, versione) prima dell'invio.
 */
@DisplayName("EnteRicevutaApiService - spedizione HTTP della ricevuta")
class EnteRicevutaApiServiceHttpTest {

    private static final String COD_APP = "APP1";
    private static final String COD_CONN = "CONN1";
    private static final String BASE_URL = "https://ente.example.com";
    private static final long CONNECT_TIMEOUT_MS = 1_000L;
    private static final long READ_TIMEOUT_MS = 2_000L;

    private ConnettoreService connettoreService;
    private ApplicazioneRepository applicazioneRepository;
    private RicevutaV2Mapper mapper;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        connettoreService = mock(ConnettoreService.class);
        applicazioneRepository = mock(ApplicazioneRepository.class);
        mapper = mock(RicevutaV2Mapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ApplicazioneEntity app = ApplicazioneEntity.builder()
                .codApplicazione(COD_APP).codConnettoreIntegrazione(COD_CONN).build();
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(app));
        when(connettoreService.getConnettoreAsMap(COD_CONN)).thenReturn(Map.of("VERSIONE", "REST_2"));
        when(mapper.toRicevuta(any())).thenReturn(new Ricevuta());
    }

    private EnteRicevutaApiService serviceWith(RestTemplate restTemplate, String baseUrl) {
        when(connettoreService.getConnettore(COD_CONN))
                .thenReturn(Connettore.builder().idConnettore(COD_CONN).url(baseUrl).build());
        when(connettoreService.getRestTemplate(COD_CONN)).thenReturn(restTemplate);

        EnteApiClientConfig config = new EnteApiClientConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");

        return new EnteRicevutaApiService(connettoreService, applicazioneRepository, config, mapper,
                transactionManager, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private Notifica notifica() {
        return Notifica.builder()
                .id(1L)
                .applicazione(Applicazione.builder().codApplicazione(COD_APP).build())
                .build();
    }

    private Rpt rpt() {
        return Rpt.builder()
                .codDominio("12345678901")
                .iuv("01234567890123456")
                .ccp("CCP1")
                .build();
    }

    @Test
    @DisplayName("PUT /ricevute/{idDominio}/{iuv}/{idRicevuta} con i dati dell'RPT")
    void sendsPutOnTheV2ReceiptEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        EnteRicevutaApiService service = serviceWith(restTemplate, BASE_URL);

        mockServer.expect(requestTo(BASE_URL + "/ricevute/12345678901/01234567890123456/CCP1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        ResponseEntity<Void> response = service.sendRicevuta(notifica(), rpt());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        mockServer.verify();
        verify(mapper).toRicevuta(any());
    }

    @Test
    @DisplayName("l'istanza API e' creata una sola volta per connettore e ricreata dopo clearCache")
    void apiInstanceIsCachedPerConnector() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        EnteRicevutaApiService service = serviceWith(restTemplate, BASE_URL);

        mockServer.expect(ExpectedCount.twice(), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess());

        service.sendRicevuta(notifica(), rpt());
        service.sendRicevuta(notifica(), rpt());

        verify(connettoreService, times(1)).getRestTemplate(COD_CONN);

        service.clearCache();
        mockServer.reset();
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL))).andRespond(withSuccess());
        service.sendRicevuta(notifica(), rpt());

        verify(connettoreService, times(2)).getRestTemplate(COD_CONN);
        verify(connettoreService).clearCache();
    }

    @Test
    @DisplayName("su SimpleClientHttpRequestFactory i timeout configurati vengono applicati")
    void timeoutsAreAppliedOnTheDefaultRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(factory);
        // Endpoint locale su porta chiusa: la connessione viene rifiutata subito, senza
        // risoluzione DNS ne' attesa. Serve solo ad attraversare la creazione dell'istanza
        // API, dove i timeout vengono applicati alla factory.
        EnteRicevutaApiService service = serviceWith(restTemplate, "http://127.0.0.1:1");

        assertThrows(RestClientException.class, () -> service.sendRicevuta(notifica(), rpt()));

        assertEquals((int) CONNECT_TIMEOUT_MS, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals((int) READ_TIMEOUT_MS, ReflectionTestUtils.getField(factory, "readTimeout"));
    }

    @Test
    @DisplayName("factory non gestita (es. mTLS da govpay-common) -> configurazione SSL preservata, timeout non toccati")
    void unknownRequestFactoryIsLeftUntouched() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        // bindTo sostituisce la request factory con quella del mock server: non e' una
        // SimpleClientHttpRequestFactory, quindi il service deve lasciarla invariata.
        EnteRicevutaApiService service = serviceWith(restTemplate, BASE_URL);
        Object factoryPrimaDellInvio = restTemplate.getRequestFactory();
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL))).andRespond(withSuccess());

        ResponseEntity<Void> response = service.sendRicevuta(notifica(), rpt());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(factoryPrimaDellInvio, restTemplate.getRequestFactory());
        mockServer.verify();
    }
}
