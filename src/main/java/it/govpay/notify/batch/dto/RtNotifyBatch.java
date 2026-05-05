package it.govpay.notify.batch.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Batch of notify missed receipt
 */
@Data
@Builder
public class RtNotifyBatch {
	private Long rtId;
    private String esito;
    private LocalDateTime notifyTime;
    private String message;
}
