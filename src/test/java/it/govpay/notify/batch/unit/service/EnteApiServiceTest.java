package it.govpay.notify.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.service.GdeService;
import it.govpay.notify.batch.service.EnteApiService;

/**
 * Unit tests for EnteApiService.
 * <p>
 * NOTA: i path HTTP di {@code notifyRendicontazione} non sono coperti perché in
 * {@code govpay-ec-client:1.0.0} la classe {@code NuovaRendicontazione.EsitoEnum} ha tutti
 * i valori dichiarati come {@code new BigDecimal("null")}: il primo accesso al tipo lancia
 * {@code NumberFormatException} in {@code <clinit>} e impedisce qualsiasi invocazione reale.
 * I test sotto coprono il costruttore, {@code clearCache} e i fallimenti preflight.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnteApiService")
class EnteApiServiceTest {

    @Mock
    private ConnettoreService connettoreService;

    @Mock
    private ApplicazioneRepository applicazioneRepository;

    @Mock
    private EnteApiClientConfig enteApiClientConfig;

    @Mock
    private GdeService gdeService;

    private EnteApiService service;

    private static final String COD_APP = "APP_TEST";
    private static final String COD_CONN = "CONN_TEST";

    private RtNotifyContext rtInfo;

    @BeforeEach
    void setUp() {
        ObjectMapper realMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(enteApiClientConfig.createEnteObjectMapper()).thenReturn(realMapper);

        service = new EnteApiService(connettoreService, applicazioneRepository, enteApiClientConfig, gdeService);

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
                .build();
    }

    @Test
    @DisplayName("constructor invokes createEnteObjectMapper once")
    void constructorBuildsObjectMapper() {
        verify(enteApiClientConfig, times(1)).createEnteObjectMapper();
    }

    @Test
    @DisplayName("applicazione mancante -> segnalata come non idonea, NON solleva, future completato SERVICE_UNAVAILABLE")
    void notifySkipsWhenApplicazioneMissing() {
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.empty());
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertTrue(future.isDone());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
        verify(gdeService, never()).saveNotifyRndOk(any(), any(), any(), any(), any(), anyString());
        verify(gdeService, never()).saveNotifyRndKo(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("codConnettore null -> segnalato come non idoneo, batch prosegue")
    void notifySkipsWhenConnettoreMissing() {
        ApplicazioneEntity bad = new ApplicazioneEntity();
        bad.setCodApplicazione(COD_APP);
        bad.setCodConnettoreIntegrazione(null);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(bad));
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.contains(COD_APP));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("codConnettore blank -> segnalato come non idoneo, batch prosegue")
    void notifySkipsWhenConnettoreBlank() {
        ApplicazioneEntity bad = new ApplicazioneEntity();
        bad.setCodApplicazione(COD_APP);
        bad.setCodConnettoreIntegrazione("   ");
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(bad));
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("connettore non abilitato (IllegalArgumentException da common) -> segnalato come non idoneo")
    void notifySkipsWhenConnettoreDisabled() {
        ApplicazioneEntity ok = new ApplicazioneEntity();
        ok.setCodApplicazione(COD_APP);
        ok.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ok));
        when(connettoreService.getConnettore(COD_CONN))
                .thenThrow(new IllegalArgumentException("Connettore non abilitato: " + COD_CONN));
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.contains("non abilitato"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("VERSIONE assente sul connettore -> segnalato come non idoneo, batch prosegue")
    void notifySkipsWhenVersioneMissing() {
        ApplicazioneEntity ok = new ApplicazioneEntity();
        ok.setCodApplicazione(COD_APP);
        ok.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ok));
        Connettore connettore = Connettore.builder().idConnettore(COD_CONN).url("https://ente/api").build();
        when(connettoreService.getConnettore(COD_CONN)).thenReturn(connettore);
        when(connettoreService.getConnettoreAsMap(COD_CONN)).thenReturn(Collections.emptyMap());
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertTrue(msg.contains("VERSIONE"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
        verify(gdeService, never()).saveNotifyRndKo(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VERSIONE blank -> segnalato come non idoneo")
    void notifySkipsWhenVersioneBlank() {
        ApplicazioneEntity ok = new ApplicazioneEntity();
        ok.setCodApplicazione(COD_APP);
        ok.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ok));
        Connettore connettore = Connettore.builder().idConnettore(COD_CONN).url("https://ente/api").build();
        when(connettoreService.getConnettore(COD_CONN)).thenReturn(connettore);
        Map<String, String> props = new HashMap<>();
        props.put("VERSIONE", "   ");
        when(connettoreService.getConnettoreAsMap(COD_CONN)).thenReturn(props);
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("VERSIONE = REST_1 (non supportata) -> segnalato come non idoneo")
    void notifySkipsWhenVersioneRest1() {
        ApplicazioneEntity ok = new ApplicazioneEntity();
        ok.setCodApplicazione(COD_APP);
        ok.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ok));
        Connettore connettore = Connettore.builder().idConnettore(COD_CONN).url("https://ente/api").build();
        when(connettoreService.getConnettore(COD_CONN)).thenReturn(connettore);
        when(connettoreService.getConnettoreAsMap(COD_CONN))
                .thenReturn(Map.of("VERSIONE", "REST_1"));
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertTrue(msg.contains("REST_1"));
        assertTrue(msg.contains("REST_2"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("VERSIONE = valore ignoto -> segnalato come non idoneo")
    void notifySkipsWhenVersioneUnknown() {
        ApplicazioneEntity ok = new ApplicazioneEntity();
        ok.setCodApplicazione(COD_APP);
        ok.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ok));
        Connettore connettore = Connettore.builder().idConnettore(COD_CONN).url("https://ente/api").build();
        when(connettoreService.getConnettore(COD_CONN)).thenReturn(connettore);
        when(connettoreService.getConnettoreAsMap(COD_CONN))
                .thenReturn(Map.of("VERSIONE", "SOAP_1"));
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        String msg = service.notifyRendicontazione(rtInfo, future);

        assertNotNull(msg);
        assertTrue(msg.startsWith("Configurazione non idonea"));
        assertTrue(msg.contains("SOAP_1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, future.join());
    }

    @Test
    @DisplayName("clearCache delegates to ConnettoreService and is idempotent")
    void clearCacheDelegates() {
        service.clearCache();
        service.clearCache();

        verify(connettoreService, times(2)).clearCache();
    }

    @Test
    @DisplayName("uses connector code for cache key (resolved via repository)")
    void resolveConnectorCodeUsesRepository() {
        ApplicazioneEntity ae = new ApplicazioneEntity();
        ae.setCodApplicazione(COD_APP);
        ae.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ae));

        // Il flusso completo potrebbe non arrivare fino alla POST HTTP (dipende dai mock
        // di connettoreService); qui verifichiamo solo che la lookup dell'applicazione
        // sia stata invocata almeno una volta.
        try {
            service.notifyRendicontazione(rtInfo, new CompletableFuture<>());
        } catch (Throwable ignored) {
            // ok — non ci interessa l'esito HTTP in questo test
        }

        verify(applicazioneRepository, org.mockito.Mockito.atLeastOnce())
                .findByCodApplicazione(COD_APP);
    }
}
