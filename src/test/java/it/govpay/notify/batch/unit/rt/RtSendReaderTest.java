package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.entity.StatoSpedizione;
import it.govpay.notify.batch.entity.TipoNotifica;
import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.rt.dto.NotificaContext;
import it.govpay.notify.batch.rt.tasklet.RtSendReader;

@DisplayName("RtSendReader")
class RtSendReaderTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0, 0);
    private static final int PAGE_SIZE = 50;

    private NotificheRepository repo;
    private RtSendReader reader;

    @BeforeEach
    void setUp() {
        repo = mock(NotificheRepository.class);
        reader = new RtSendReader(repo, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), PAGE_SIZE);
    }

    private Notifica notifica(long id) {
        return Notifica.builder()
                .id(id)
                .rpt(Rpt.builder().id(id * 10).iuv("IUV" + id).build())
                .build();
    }

    private void stubNotifiche(Notifica... notifiche) {
        when(repo.findNotificheDaSpedire(eq(TipoNotifica.RICEVUTA), eq(StatoSpedizione.DA_SPEDIRE),
                eq(NOW), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(notifiche));
    }

    @Test
    @DisplayName("interroga le notifiche RICEVUTA/DA_SPEDIRE scadute alla data del clock, una pagina di pageSize")
    void queryUsesExpectedFilters() {
        stubNotifiche();

        reader.read();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findNotificheDaSpedire(eq(TipoNotifica.RICEVUTA), eq(StatoSpedizione.DA_SPEDIRE),
                eq(NOW), pageable.capture());
        assertEquals(PageRequest.of(0, PAGE_SIZE), pageable.getValue());
    }

    @Test
    @DisplayName("nessuna notifica da spedire -> read() restituisce subito null")
    void emptyBatchReturnsNull() {
        stubNotifiche();

        assertNull(reader.read());
    }

    @Test
    @DisplayName("restituisce un NotificaContext per notifica, in ordine, poi null a coda esaurita")
    void drainsBufferInOrderThenReturnsNull() {
        Notifica prima = notifica(1L);
        Notifica seconda = notifica(2L);
        stubNotifiche(prima, seconda);

        NotificaContext ctx1 = reader.read();
        NotificaContext ctx2 = reader.read();

        assertSame(prima, ctx1.getNotifica());
        assertSame(prima.getRpt(), ctx1.getRpt());
        assertSame(seconda, ctx2.getNotifica());
        assertSame(seconda.getRpt(), ctx2.getRpt());
        assertNull(reader.read());
    }

    @Test
    @DisplayName("il batch e' caricato una sola volta: le read successive svuotano il buffer senza rileggere dal DB")
    void loadsBatchOnlyOnce() {
        stubNotifiche(notifica(1L), notifica(2L));

        reader.read();
        reader.read();
        reader.read();

        verify(repo, times(1)).findNotificheDaSpedire(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("init() (@BeforeStep) azzera il buffer: l'esecuzione successiva rilegge dal DB")
    void initResetsBufferBetweenSteps() {
        stubNotifiche(notifica(1L));

        assertEquals(1L, reader.read().getNotifica().getId());
        assertNull(reader.read());

        reader.init();

        // Senza il reset la read tornerebbe null usando il buffer svuotato della run precedente.
        assertEquals(1L, reader.read().getNotifica().getId());
        verify(repo, times(2)).findNotificheDaSpedire(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
