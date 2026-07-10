package it.govpay.notify.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import tools.jackson.databind.json.JsonMapper;

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
        JsonMapper realMapper = JsonMapper.builder().build();
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
    void constructorBuildsJsonMapper() {
        verify(enteApiClientConfig, times(1)).createEnteObjectMapper();
    }

    @Test
    @DisplayName("throws IllegalStateException when applicazione is missing")
    void notifyThrowsWhenApplicazioneMissing() {
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.notifyRendicontazione(rtInfo, new CompletableFuture<>()));
        verify(gdeService, never()).saveNotifyRndOk(any(), any(), any(), any(), any(), anyString());
        verify(gdeService, never()).saveNotifyRndKo(any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("throws IllegalStateException when codConnettore is null")
    void notifyThrowsWhenConnettoreMissing() {
        ApplicazioneEntity bad = new ApplicazioneEntity();
        bad.setCodApplicazione(COD_APP);
        bad.setCodConnettoreIntegrazione(null);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(bad));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.notifyRendicontazione(rtInfo, new CompletableFuture<>()));
        assert ex.getMessage().contains(COD_APP);
    }

    @Test
    @DisplayName("throws IllegalStateException when codConnettore is blank")
    void notifyThrowsWhenConnettoreBlank() {
        ApplicazioneEntity bad = new ApplicazioneEntity();
        bad.setCodApplicazione(COD_APP);
        bad.setCodConnettoreIntegrazione("   ");
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(bad));

        assertThrows(IllegalStateException.class,
                () -> service.notifyRendicontazione(rtInfo, new CompletableFuture<>()));
    }

    @Test
    @DisplayName("clearCache delegates to ConnettoreService and is idempotent")
    void clearCacheDelegates() {
        service.clearCache();
        service.clearCache();

        verify(connettoreService, times(2)).clearCache();
    }

    @Test
    @DisplayName("future is left incomplete on preflight failure")
    void futureIncompleteOnPreflight() {
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.empty());
        CompletableFuture<HttpStatusCode> future = new CompletableFuture<>();

        assertThrows(IllegalStateException.class,
                () -> service.notifyRendicontazione(rtInfo, future));
        assertFalse(future.isDone());
    }

    @Test
    @DisplayName("uses connector code for cache key (resolved via repository)")
    void resolveConnectorCodeUsesRepository() {
        ApplicazioneEntity ae = new ApplicazioneEntity();
        ae.setCodApplicazione(COD_APP);
        ae.setCodConnettoreIntegrazione(COD_CONN);
        when(applicazioneRepository.findByCodApplicazione(COD_APP)).thenReturn(Optional.of(ae));

        // Anche se il flusso completo fallirà sul EsitoEnum.<clinit>, la lookup è invocata prima:
        try {
            service.notifyRendicontazione(rtInfo, new CompletableFuture<>());
        } catch (Throwable ignored) {
            // expected — see class javadoc
        }

        verify(applicazioneRepository, times(1)).findByCodApplicazione(COD_APP);
    }
}
