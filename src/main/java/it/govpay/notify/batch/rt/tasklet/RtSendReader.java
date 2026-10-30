package it.govpay.notify.batch.rt.tasklet;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.StatoSpedizione;
import it.govpay.notify.batch.entity.TipoNotifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.repository.PendenzaNotificaRepository;
import it.govpay.notify.batch.rt.dto.NotificaContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader paginato delle notifiche RICEVUTA pronte alla spedizione.
 * Riproduce {@code NotificheBD.findNotificheDaSpedire} del monolite.
 * <p>
 * Pendenza e riscossioni si leggono al momento della consegna di ogni notifica, quindi
 * nella transazione del chunk che la elabora, con govpay-common-pendenze.
 */
@Component
@StepScope
@Slf4j
public class RtSendReader implements ItemReader<NotificaContext> {

    private final NotificheRepository notificheRepository;
    private final PendenzaNotificaRepository pendenzaNotificaRepository;
    private final Clock clock;
    private final int pageSize;

    private List<Notifica> buffer;

    public RtSendReader(NotificheRepository notificheRepository,
                        PendenzaNotificaRepository pendenzaNotificaRepository,
                        Clock clock,
                        @Value("${govpay.batch.rt-send.chunk-size:50}") int pageSize) {
        this.notificheRepository = notificheRepository;
        this.pendenzaNotificaRepository = pendenzaNotificaRepository;
        this.clock = clock;
        this.pageSize = pageSize;
    }

    @BeforeStep
    public void init() {
        this.buffer = null;
    }

    @Override
    public NotificaContext read() {
        if (buffer == null) {
            loadBatch();
        }
        if (buffer.isEmpty()) {
            return null;
        }
        return toContext(buffer.remove(0));
    }

    private NotificaContext toContext(Notifica notifica) {
        Rpt rpt = notifica.getRpt();
        return NotificaContext.builder()
                .notifica(notifica)
                .rpt(rpt)
                .pendenza(rpt.getIdVersamento() != null
                        ? pendenzaNotificaRepository.findConVoci(rpt.getIdVersamento()).orElse(null)
                        : null)
                .pagamenti(pendenzaNotificaRepository.findPagamenti(rpt.getId()))
                .build();
    }

    private void loadBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Notifica> notifiche = notificheRepository.findNotificheDaSpedire(
                TipoNotifica.RICEVUTA,
                StatoSpedizione.DA_SPEDIRE,
                now,
                PageRequest.of(0, pageSize));

        log.info("Trovate {} notifiche RT da spedire", notifiche.size());

        buffer = new ArrayList<>(notifiche);
    }
}
