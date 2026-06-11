package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.entity.Applicazione;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.rt.service.EnteRicevutaApiService;
import it.govpay.notify.batch.rt.service.RicevutaV2Mapper;

@DisplayName("EnteRicevutaApiService - validazioni precondizioni")
class EnteRicevutaApiServiceTest {

    private ConnettoreService connettoreService;
    private ApplicazioneRepository applicazioneRepository;
    private RicevutaV2Mapper mapper;
    private EnteRicevutaApiService service;

    @BeforeEach
    void setUp() {
        connettoreService = mock(ConnettoreService.class);
        applicazioneRepository = mock(ApplicazioneRepository.class);
        mapper = mock(RicevutaV2Mapper.class);

        EnteApiClientConfig config = new EnteApiClientConfig();
        // valore minimo necessario per createEnteObjectMapper
        org.springframework.test.util.ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");

        service = new EnteRicevutaApiService(connettoreService, applicazioneRepository,
                config, mapper, 1000L, 1000L);
    }

    private Notifica notificaFor(String codApplicazione) {
        return Notifica.builder()
                .id(1L)
                .applicazione(Applicazione.builder().codApplicazione(codApplicazione).build())
                .build();
    }

    @Test
    @DisplayName("applicazione assente -> IllegalStateException")
    void applicationMissing() {
        when(applicazioneRepository.findByCodApplicazione(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.sendRicevuta(notificaFor("APP1"), Rpt.builder().build()));
    }

    @Test
    @DisplayName("codConnettoreIntegrazione assente -> IllegalStateException")
    void connectorCodeMissing() {
        when(applicazioneRepository.findByCodApplicazione("APP1"))
                .thenReturn(Optional.of(ApplicazioneEntity.builder()
                        .codApplicazione("APP1")
                        .codConnettoreIntegrazione(null)
                        .build()));
        assertThrows(IllegalStateException.class,
                () -> service.sendRicevuta(notificaFor("APP1"), Rpt.builder().build()));
    }

    @Test
    @DisplayName("connettore non configurato (map vuota) -> IllegalStateException")
    void connectorPropsEmpty() {
        when(applicazioneRepository.findByCodApplicazione("APP1"))
                .thenReturn(Optional.of(ApplicazioneEntity.builder()
                        .codApplicazione("APP1").codConnettoreIntegrazione("CONN1").build()));
        when(connettoreService.getConnettoreAsMap("CONN1")).thenReturn(Map.of());
        assertThrows(IllegalStateException.class,
                () -> service.sendRicevuta(notificaFor("APP1"), Rpt.builder().build()));
    }

    @Test
    @DisplayName("VERSIONE != REST_2 -> UnsupportedOperationException")
    void unsupportedVersion() {
        when(applicazioneRepository.findByCodApplicazione("APP1"))
                .thenReturn(Optional.of(ApplicazioneEntity.builder()
                        .codApplicazione("APP1").codConnettoreIntegrazione("CONN1").build()));
        when(connettoreService.getConnettoreAsMap("CONN1"))
                .thenReturn(Map.of("VERSIONE", "REST_1"));
        assertThrows(UnsupportedOperationException.class,
                () -> service.sendRicevuta(notificaFor("APP1"), Rpt.builder().build()));
    }

    @Test
    @DisplayName("VERSIONE assente nella map -> UnsupportedOperationException")
    void missingVersion() {
        when(applicazioneRepository.findByCodApplicazione("APP1"))
                .thenReturn(Optional.of(ApplicazioneEntity.builder()
                        .codApplicazione("APP1").codConnettoreIntegrazione("CONN1").build()));
        when(connettoreService.getConnettoreAsMap("CONN1"))
                .thenReturn(Map.of("URL", "https://ente.example.com"));
        assertThrows(UnsupportedOperationException.class,
                () -> service.sendRicevuta(notificaFor("APP1"), Rpt.builder().build()));
    }
}
