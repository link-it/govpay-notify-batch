package it.govpay.notify.batch.unit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.ObjectMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.mapper.EventoRtMapper;
import it.govpay.notify.batch.gde.service.GdeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GdeService")
class GdeServiceTest {

    @Mock
    private ConfigurazioneService configurazioneService;

    @Mock
    private EventoRtMapper eventoRtMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate gdeRestTemplate;

    // Use synchronous executor for predictable test execution
    private final Executor syncExecutor = Runnable::run;

    private GdeService gdeService;
    private RtNotifyContext rtInfo;
    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String ENTE_BASE_URL = "https://ente.example.com/api";
    private static final String GDE_ENDPOINT = "http://gde-service/api/v1/eventi";

    @BeforeEach
    void setUp() {
        gdeService = new GdeService(objectMapper, syncExecutor, configurazioneService, eventoRtMapper);

        rtInfo = RtNotifyContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        dataStart = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2024, 1, 15, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    private void setupGdeEnabled() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(gdeRestTemplate);
        Connettore gdeConnettore = new Connettore();
        gdeConnettore.setUrl("http://gde-service/api/v1");
        when(configurazioneService.getServizioGDE()).thenReturn(gdeConnettore);
    }

    @Nested
    @DisplayName("sendEventAsync")
    class SendEventAsyncTest {

        @Test
        @DisplayName("should send event when GDE is enabled")
        void shouldSendEventWhenGdeEnabled() {
            setupGdeEnabled();
            NuovoEvento evento = new NuovoEvento();
            evento.setTipoEvento("TEST");

            gdeService.sendEventAsync(evento);

            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(evento), eq(Void.class));
        }

        @Test
        @DisplayName("should not send event when GDE is disabled")
        void shouldNotSendEventWhenGdeDisabled() {
            when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);
            NuovoEvento evento = new NuovoEvento();

            gdeService.sendEventAsync(evento);

            verifyNoInteractions(gdeRestTemplate);
        }

        @Test
        @DisplayName("should handle API exception gracefully")
        void shouldHandleApiExceptionGracefully() {
            setupGdeEnabled();
            NuovoEvento evento = new NuovoEvento();
            when(gdeRestTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                    .thenThrow(new RuntimeException("API Error"));

            assertDoesNotThrow(() -> gdeService.sendEventAsync(evento));
        }
    }

    @Nested
    @DisplayName("saveNotifyRndOk")
    class SaveNotifyRndOkTest {

        @Test
        @DisplayName("should create and send OK event for successful notify")
        void shouldCreateAndSendOkEventForSuccessfulNotify() {
            setupGdeEnabled();
            ResponseEntity<Void> response = ResponseEntity.ok().build();
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.OK);

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveNotifyRndOk(rtInfo, "{}", response, dataStart, dataEnd, ENTE_BASE_URL);

            verify(eventoRtMapper).createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), contains("/rendicontazioni"), eq("POST"), anyList(), eq("{}"));
            verify(eventoRtMapper).setParametriRisposta(eq(mockEvento), eq(dataEnd), eq(response), isNull());
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("payload di risposta")
    class ResponsePayloadTest {

        @Test
        @DisplayName("parametri di risposta gia' presenti -> il payload della response viene valorizzato")
        void payloadIsSetWhenParametriRispostaArePresent() {
            setupGdeEnabled();
            ResponseEntity<String> response = ResponseEntity.ok("corpo-risposta");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setParametriRisposta(new DettaglioRisposta());
            // Il payload viene serializzato dall'ObjectMapper iniettato in AbstractGdeService.
            when(objectMapper.writeValueAsString("corpo-risposta")).thenReturn("\"corpo-risposta\"");

            when(eventoRtMapper.createEventoOk(eq(rtInfo), anyString(), anyString(), eq(dataStart), eq(dataEnd)))
                    .thenReturn(mockEvento);

            gdeService.saveNotifyRndOk(rtInfo, "{}", response, dataStart, dataEnd, ENTE_BASE_URL);

            // Il GDE vuole il payload in base64 (cfr. GdeUtils.extractResponsePayload).
            String atteso = java.util.Base64.getEncoder()
                    .encodeToString("\"corpo-risposta\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(atteso, mockEvento.getParametriRisposta().getPayload());
        }
    }

    @Nested
    @DisplayName("pattern GdeEventInfo non supportato")
    class UnsupportedGdeEventInfoTest {

        /**
         * GdeService invia gli eventi con sendEventAsync(NuovoEvento), non con il
         * pattern a GdeEventInfo di AbstractGdeService: i due hook della superclasse
         * sono implementati per contratto e devono restare inutilizzabili, cosi' che
         * un uso accidentale emerga subito invece di produrre eventi vuoti.
         */
        @Test
        @DisplayName("convertToGdeEvent e getConfigurazioneComponente sollevano UnsupportedOperationException")
        void gdeEventInfoHooksAreNotSupported() {
            assertThrows(UnsupportedOperationException.class, () -> org.springframework.test.util.ReflectionTestUtils
                    .invokeMethod(gdeService, "convertToGdeEvent", (Object) null));
            assertThrows(UnsupportedOperationException.class, () -> org.springframework.test.util.ReflectionTestUtils
                    .invokeMethod(gdeService, "getConfigurazioneComponente", null, null));
        }
    }

    @Nested
    @DisplayName("saveNotifyRndKo")
    class SaveNotifyRndKoTest {

        @Test
        @DisplayName("should create and send KO event for failed notify")
        void shouldCreateAndSendKoEventForFailedNotify() {
            setupGdeEnabled();
            RestClientException exception = new RestClientException("API Error");
            NuovoEvento mockEvento = new NuovoEvento();
            mockEvento.setEsito(EsitoEvento.KO);

            when(eventoRtMapper.createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), eq(exception)))
                    .thenReturn(mockEvento);

            gdeService.saveNotifyRndKo(rtInfo, "{}", null, exception, dataStart, dataEnd, ENTE_BASE_URL);

            verify(eventoRtMapper).createEventoKo(eq(rtInfo), anyString(), anyString(),
                    eq(dataStart), eq(dataEnd), isNull(), eq(exception));
            verify(eventoRtMapper).setParametriRichiesta(eq(mockEvento), contains("/rendicontazioni"), eq("POST"), anyList(), eq("{}"));
            verify(gdeRestTemplate).postForEntity(eq(GDE_ENDPOINT), eq(mockEvento), eq(Void.class));
        }
    }
}
