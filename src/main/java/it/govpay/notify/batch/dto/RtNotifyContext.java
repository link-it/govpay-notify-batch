package it.govpay.notify.batch.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for processing missing receipt notify
 */
@Data
@Builder(toBuilder = true)
public class RtNotifyContext {
	private Long rtId;
    private String taxCode;
    private String iuv;
    private String iur;
    // Campi numerici mantenuti come tipi boxed nullable: il reader legge colonne del ResultSet
    // che nel legacy possono essere NULL (in particolare fr.revisione). Usare primitivi qui
    // porterebbe a NPE da auto-unboxing nel builder Lombok, con failure dello step (stesso
    // pattern del bug fixato in 1.0.7 su convertToXxx).
    private Integer indice;
    private BigDecimal importo;
    private Integer esito;
    private OffsetDateTime data;
    private String idFlusso;
    private OffsetDateTime dataFlusso;
    private String trn;
    private OffsetDateTime dataRegolamento;
    private OffsetDateTime dataOraPubblicazione;
    private OffsetDateTime dataOraAggiornamento;
    private String idPsp;
    private String bicRiversamento;
    private Integer revisione;
    private String codApplicazione;
}
