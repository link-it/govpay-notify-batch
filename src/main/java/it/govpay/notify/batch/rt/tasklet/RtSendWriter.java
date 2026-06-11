package it.govpay.notify.batch.rt.tasklet;

import java.time.LocalDateTime;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer del job RT. Applica l'aggiornamento di stato sulla tabella
 * {@code notifiche} con una transazione per item, replicando i tre
 * scenari del monolite ({@code NotificheBD}):
 * <ul>
 *   <li>SUCCESS -> updateSpedito</li>
 *   <li>ERROR   -> updateDaSpedire con backoff quadratico (tentativi^2 * 60s, capped a 24h)</li>
 *   <li>ABORT   -> updateAnnullata con data prossima spedizione "mai" (9999-02-01)</li>
 * </ul>
 */
@Component
@Slf4j
public class RtSendWriter implements ItemWriter<RtSendResult> {

    /** Sentinel "mai" usata anche dal monolite (cfr. InviaNotificaThread). */
    private static final LocalDateTime DATA_PROSSIMA_SPEDIZIONE_MAI =
            LocalDateTime.of(9999, 2, 1, 0, 0);

    private static final long ONE_DAY_SECONDS = 24L * 60 * 60;

    private final NotificheRepository notificheRepository;

    public RtSendWriter(NotificheRepository notificheRepository) {
        this.notificheRepository = notificheRepository;
    }

    @Override
    public void write(Chunk<? extends RtSendResult> chunk) {
        for (RtSendResult r : chunk) {
            if (r == null) {
                continue;
            }
            applyUpdate(r);
        }
    }

    /**
     * Update di stato della singola notifica in transazione propria
     * (REQUIRES_NEW): se la riga successiva fallisce, le precedenti
     * restano committate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyUpdate(RtSendResult r) {
        LocalDateTime now = LocalDateTime.now();
        switch (r.getOutcome()) {
            case SUCCESS -> notificheRepository.updateSpedito(r.getNotificaId(), now);
            case ERROR -> {
                LocalDateTime prossima = nextRetry(now, r.getTentativiSpedizione());
                notificheRepository.updateDaSpedire(r.getNotificaId(),
                        r.getDescrizione(), r.getTentativiSpedizione(), prossima, now);
            }
            case ABORT -> notificheRepository.updateAnnullata(r.getNotificaId(),
                    r.getDescrizione(), r.getTentativiSpedizione(),
                    DATA_PROSSIMA_SPEDIZIONE_MAI, now);
        }
        log.debug("Aggiornato stato notifica RT id={} outcome={}", r.getNotificaId(), r.getOutcome());
    }

    /**
     * Backoff quadratico: {@code now + tentativi^2 * 60s}, capped a 24h
     * (cfr. {@code InviaNotificaThread} del monolite).
     */
    private LocalDateTime nextRetry(LocalDateTime now, Long tentativi) {
        long delaySeconds = Math.min(tentativi * tentativi * 60L, ONE_DAY_SECONDS);
        return now.plusSeconds(delaySeconds);
    }
}
