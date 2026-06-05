package it.govpay.notify.batch.tasklet;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import it.govpay.notify.batch.dto.RtNotifyBatch;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.service.EnteApiService;

/**
 * Processor to notify missing receipt to application
 */
@Component
@Slf4j
public class RtNotifyProcessor implements ItemProcessor<RtNotifyContext, RtNotifyBatch> {


	private final EnteApiService enteApiService;

	public RtNotifyProcessor(EnteApiService enteApiService) {
		this.enteApiService = enteApiService;
    }

    @Override
    public RtNotifyBatch process(RtNotifyContext context) throws Exception {
        log.info("Processing rendicontazione {}: {} - {} - {}",
                 context.getRtId(), context.getTaxCode(), context.getIur(), context.getIuv());

        CompletableFuture<HttpStatusCode> statusCodeFuture = new CompletableFuture<>();
        String msg = enteApiService.notifyRendicontazione(context, statusCodeFuture);
        String esito = (statusCodeFuture.isDone() && (statusCodeFuture.get().is2xxSuccessful() || statusCodeFuture.get().is4xxClientError()) ? "OK" : "KO");
        return RtNotifyBatch.builder()
                              .rtId(context.getRtId())
                              .esito(esito)
                              .notifyTime(LocalDateTime.now())
                              .message(msg)
                              .build();
    }
}
