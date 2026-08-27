package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.rt.dto.RtSendOutcome;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import it.govpay.notify.batch.rt.tasklet.RtSendStatusUpdater;
import it.govpay.notify.batch.rt.tasklet.RtSendWriter;

@DisplayName("RtSendWriter")
class RtSendWriterTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0, 0);

    private NotificheRepository repo;
    private RtSendWriter writer;

    @BeforeEach
    void setUp() {
        repo = mock(NotificheRepository.class);
        // Clock fisso: le date calcolate dal writer sono deterministiche.
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        writer = new RtSendWriter(new RtSendStatusUpdater(repo, clock));
    }

    @Test
    @DisplayName("SUCCESS -> updateSpedito; gli altri update non vengono chiamati")
    void success() {
        RtSendResult r = RtSendResult.builder()
                .notificaId(10L)
                .outcome(RtSendOutcome.SUCCESS)
                .build();

        writer.write(new Chunk<>(List.of(r)));

        verify(repo).updateSpedito(10L, NOW);
        verify(repo, never()).updateDaSpedire(any(), any(), any(), any(), any());
        verify(repo, never()).updateAnnullata(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ABORT -> updateAnnullata con sentinel \"mai\" (9999-02-01)")
    void abort() {
        RtSendResult r = RtSendResult.builder()
                .notificaId(11L)
                .outcome(RtSendOutcome.ABORT)
                .descrizione("connettore assente")
                .tentativiSpedizione(1L)
                .build();

        writer.write(new Chunk<>(List.of(r)));

        ArgumentCaptor<LocalDateTime> prossima = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).updateAnnullata(eq(11L), eq("connettore assente"), eq(1L),
                prossima.capture(), eq(NOW));
        assertEquals(LocalDateTime.of(9999, 2, 1, 0, 0), prossima.getValue());
    }

    @Test
    @DisplayName("ERROR -> updateDaSpedire con backoff quadratico (tentativi^2 * 60s)")
    void errorBackoff() {
        RtSendResult r = RtSendResult.builder()
                .notificaId(12L)
                .outcome(RtSendOutcome.ERROR)
                .descrizione("HTTP 503")
                .tentativiSpedizione(3L) // 3^2 * 60s = 540s
                .build();

        writer.write(new Chunk<>(List.of(r)));

        verify(repo).updateDaSpedire(12L, "HTTP 503", 3L, NOW.plusSeconds(540), NOW);
    }

    @Test
    @DisplayName("ERROR con molti tentativi -> backoff capped a 24h")
    void errorBackoffCapped() {
        RtSendResult r = RtSendResult.builder()
                .notificaId(13L)
                .outcome(RtSendOutcome.ERROR)
                .descrizione("HTTP 500")
                .tentativiSpedizione(1000L) // 1000^2 * 60s = 16.6 anni -> cap 24h
                .build();

        writer.write(new Chunk<>(List.of(r)));

        verify(repo).updateDaSpedire(13L, "HTTP 500", 1000L, NOW.plusSeconds(24L * 60 * 60), NOW);
    }

    @Test
    @DisplayName("Chunk con elementi null saltati")
    void nullsSkipped() {
        List<RtSendResult> list = new ArrayList<>();
        list.add(null);
        list.add(RtSendResult.builder().notificaId(14L).outcome(RtSendOutcome.SUCCESS).build());

        writer.write(new Chunk<>(list));

        verify(repo).updateSpedito(14L, NOW);
    }
}
