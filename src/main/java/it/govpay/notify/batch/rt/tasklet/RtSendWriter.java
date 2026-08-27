package it.govpay.notify.batch.rt.tasklet;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import it.govpay.notify.batch.rt.dto.RtSendResult;

/**
 * Writer del job RT. Delega a {@link RtSendStatusUpdater} l'aggiornamento di
 * stato sulla tabella {@code notifiche}, che avviene con una transazione per
 * item (vedi il javadoc dell'updater per il dettaglio dei tre scenari e per
 * il motivo per cui e' un bean separato).
 */
@Component
public class RtSendWriter implements ItemWriter<RtSendResult> {

    private final RtSendStatusUpdater statusUpdater;

    public RtSendWriter(RtSendStatusUpdater statusUpdater) {
        this.statusUpdater = statusUpdater;
    }

    @Override
    public void write(Chunk<? extends RtSendResult> chunk) {
        for (RtSendResult r : chunk) {
            if (r == null) {
                continue;
            }
            statusUpdater.applyUpdate(r);
        }
    }
}
