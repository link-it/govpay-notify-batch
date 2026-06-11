package it.govpay.notify.batch.unit.rt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.rt.dto.RtSendOutcome;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import it.govpay.notify.batch.rt.tasklet.RtSendWriter;

@DisplayName("RtSendWriter")
class RtSendWriterTest {

    private NotificheRepository repo;
    private RtSendWriter writer;

    @BeforeEach
    void setUp() {
        repo = mock(NotificheRepository.class);
        writer = new RtSendWriter(repo);
    }

    @Test
    @DisplayName("SUCCESS -> updateSpedito; gli altri update non vengono chiamati")
    void success() {
        RtSendResult r = RtSendResult.builder()
                .notificaId(10L)
                .outcome(RtSendOutcome.SUCCESS)
                .build();

        writer.write(new Chunk<>(java.util.List.of(r)));

        verify(repo).updateSpedito(eq(10L), any(LocalDateTime.class));
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

        writer.write(new Chunk<>(java.util.List.of(r)));

        ArgumentCaptor<LocalDateTime> prossima = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).updateAnnullata(eq(11L), eq("connettore assente"), eq(1L),
                prossima.capture(), any(LocalDateTime.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                LocalDateTime.of(9999, 2, 1, 0, 0), prossima.getValue());
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

        LocalDateTime before = LocalDateTime.now();
        writer.write(new Chunk<>(java.util.List.of(r)));

        ArgumentCaptor<LocalDateTime> prossima = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).updateDaSpedire(eq(12L), eq("HTTP 503"), eq(3L),
                prossima.capture(), any(LocalDateTime.class));
        long secs = java.time.Duration.between(before, prossima.getValue()).toSeconds();
        org.junit.jupiter.api.Assertions.assertTrue(secs >= 539 && secs <= 541,
                "atteso ~540s, ottenuto " + secs);
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

        LocalDateTime before = LocalDateTime.now();
        writer.write(new Chunk<>(java.util.List.of(r)));

        ArgumentCaptor<LocalDateTime> prossima = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).updateDaSpedire(eq(13L), any(), eq(1000L),
                prossima.capture(), any(LocalDateTime.class));
        long secs = java.time.Duration.between(before, prossima.getValue()).toSeconds();
        org.junit.jupiter.api.Assertions.assertTrue(secs <= 24L * 60 * 60 + 1,
                "atteso <= 24h, ottenuto " + secs);
    }

    @Test
    @DisplayName("Chunk con elementi null saltati")
    void nullsSkipped() {
        java.util.List<RtSendResult> list = new java.util.ArrayList<>();
        list.add(null);
        list.add(RtSendResult.builder().notificaId(14L).outcome(RtSendOutcome.SUCCESS).build());
        writer.write(new Chunk<>(list));
        verify(repo).updateSpedito(eq(14L), any(LocalDateTime.class));
    }
}
