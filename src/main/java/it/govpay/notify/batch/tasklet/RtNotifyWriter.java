package it.govpay.notify.batch.tasklet;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.notify.batch.dto.RtNotifyBatch;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer to regisger notify
 */
@Component
@Slf4j
public class RtNotifyWriter implements ItemWriter<RtNotifyBatch> {

    private final RendicontazioniRepository rendicontazioniRepository;

    public RtNotifyWriter(RendicontazioniRepository rendicontazioniRepository) {
    	this.rendicontazioniRepository = rendicontazioniRepository;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends RtNotifyBatch> chunk) {
        for (RtNotifyBatch batch : chunk) {
            if (batch == null)
                log.info("Internal error: no notify processor output");
            else {
                if (batch.getMessage() != null)
                    log.info(batch.getMessage());
                if (batch.getEsito() != null && batch.getEsito().equals("OK"))
                	rendicontazioniRepository.registerNotificaRt(batch.getRtId());
                if (batch.getNotifyTime() != null)
                    log.info("Ricevuta notificata: id {} ", batch.getRtId());
            }
        }
    }
}
