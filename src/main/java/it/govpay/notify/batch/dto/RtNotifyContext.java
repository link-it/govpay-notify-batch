package it.govpay.notify.batch.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for processing missing receipt notify
 */
@Data
@Builder
public class RtNotifyContext {
	private Long rtId;
    private String taxCode;
    private String iuv;
    private String iur;
    private int indice;
    private BigDecimal importo;
    private int esito;
    private OffsetDateTime data;
    private String idFlusso;
    private OffsetDateTime dataFlusso;
    private String trn;
    private OffsetDateTime dataRegolamento;
    private OffsetDateTime dataOraPubblicazione;
    private OffsetDateTime dataOraAggiornamento;
    private String idPsp;
    private String bicRiversamento;
    private int revisione;
    private String codApplicazione;
}
