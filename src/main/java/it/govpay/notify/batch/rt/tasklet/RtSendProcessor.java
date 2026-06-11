package it.govpay.notify.batch.rt.tasklet;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.rt.dto.NotificaContext;
import it.govpay.notify.batch.rt.dto.RtSendOutcome;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import it.govpay.notify.batch.rt.service.EnteRicevutaApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor che spedisce la singola notifica RT all'Ente e ne deriva
 * il {@link RtSendResult} da applicare in scrittura.
 */
@Component
@Slf4j
public class RtSendProcessor implements ItemProcessor<NotificaContext, RtSendResult> {

    private final EnteRicevutaApiService enteRicevutaApiService;

    public RtSendProcessor(EnteRicevutaApiService enteRicevutaApiService) {
        this.enteRicevutaApiService = enteRicevutaApiService;
    }

    @Override
    public RtSendResult process(NotificaContext ctx) {
        Notifica notifica = ctx.getNotifica();
        long tentativiBase = notifica.getTentativiSpedizione() == null ? 0L : notifica.getTentativiSpedizione();

        try {
            ResponseEntity<Void> response = enteRicevutaApiService.sendRicevuta(notifica, ctx.getRpt());
            log.debug("Ricevuta notificata: notificaId={}, status={}", notifica.getId(), response.getStatusCode());

            return RtSendResult.builder()
                    .notificaId(notifica.getId())
                    .outcome(RtSendOutcome.SUCCESS)
                    .build();

        } catch (IllegalStateException | UnsupportedOperationException e) {
            log.warn("Notifica RT annullata (notificaId={}): {}", notifica.getId(), e.getMessage());
            return RtSendResult.builder()
                    .notificaId(notifica.getId())
                    .outcome(RtSendOutcome.ABORT)
                    .descrizione(truncate(e.getMessage()))
                    .tentativiSpedizione(tentativiBase + 1)
                    .build();

        } catch (RestClientException e) {
            log.warn("Errore spedizione RT (notificaId={}): {}", notifica.getId(), e.getMessage());
            return RtSendResult.builder()
                    .notificaId(notifica.getId())
                    .outcome(RtSendOutcome.ERROR)
                    .descrizione(truncate(e.getMessage()))
                    .tentativiSpedizione(tentativiBase + 1)
                    .build();
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        int max = Costanti.NOTIFICHE_DESCRIZIONE_STATO_MAX_LEN;
        if (message.length() < max) {
            return message;
        }
        return message.substring(0, max - 3) + "...";
    }
}
