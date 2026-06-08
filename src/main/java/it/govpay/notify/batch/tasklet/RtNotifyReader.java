package it.govpay.notify.batch.tasklet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for missing receipt to be notify.
 */
@Component
@StepScope
@Slf4j
public class RtNotifyReader implements ItemReader<RtNotifyContext>, StepExecutionListener {

    private final RendicontazioniRepository rndRepository;
    private final int finestraTemporale;

    private List<RtNotifyContext> toBeNotifyList = null;

    public RtNotifyReader(
    		RendicontazioniRepository rndRepository,
    		@Value("${govpay.batch.finestra-temporale:90}") int finestraTemporale) {
        this.rndRepository = rndRepository;
        this.finestraTemporale = finestraTemporale;
    }

    @BeforeStep
    public void initToBeNotify() {
		toBeNotifyList = new ArrayList<>();
		LocalDateTime dataLimite = LocalDateTime.now().minusDays(finestraTemporale);
		List<Object[]> rndInfos = rndRepository.findRendicontazioneWithNoPagamentoAfterId(dataLimite);
		log.info("Trovate {} ricevute da notificare", rndInfos.size());
		for (Object[] rndInfo : rndInfos) {
			log.debug("Ricevuta da recuperare id {}, taxCode {}, iuv {}, iur {}", rndInfo[0], rndInfo[1], rndInfo[2], rndInfo[3]);
			RtNotifyContext rtNotifyCtx = RtNotifyContext.builder()
			                                             .rtId(convertToLong(rndInfo[0]))
			                                             .taxCode((String)rndInfo[1])
			                                             .iuv((String)rndInfo[2])
			                                             .iur((String)rndInfo[3])
			                                             .indice(convertToInteger(rndInfo[4]))
			                                             .importo(convertToBigDecimal(rndInfo[5]))
			                                             .esito(convertToInteger(rndInfo[6]))
			                                             .data(convertToOffsetDateTime(rndInfo[7]))
			                                             .idFlusso((String)rndInfo[8])
			                                             .dataFlusso(convertToOffsetDateTime(rndInfo[9]))
			                                             .trn((String)rndInfo[10])
			                                             .dataRegolamento(convertToOffsetDateTime(rndInfo[11]))
			                                             .dataOraPubblicazione(convertToOffsetDateTime(rndInfo[12]))
			                                             .dataOraAggiornamento(convertToOffsetDateTime(rndInfo[13]))
			                                             .idPsp((String)rndInfo[14])
			                                             .bicRiversamento((String)rndInfo[15])
			                                             .revisione(convertToInteger(rndInfo[16]))
			                                             .codApplicazione((String)rndInfo[17])
			                                             .build();
			toBeNotifyList.add(rtNotifyCtx);
		}
    }

    private Long convertToLong(Object object) {
    	if (object instanceof Long longId)
    		return longId;
    	if (object instanceof BigInteger bigId)
    		return bigId.longValue();
    	throw new IllegalArgumentException("Class not convert to long" + object.getClass().getName());
	}

    private Integer convertToInteger(Object object) {
    	if (object instanceof Integer intValue)
    		return intValue.intValue();
    	if (object instanceof Long longValue)
    		return longValue.intValue();
    	if (object instanceof BigInteger bigValue)
    		return bigValue.intValue();
    	throw new IllegalArgumentException("Class not convert to integer " + object.getClass().getName());
	}

    private BigDecimal convertToBigDecimal(Object object) {
    	if (object instanceof Double doubleValue)
    		return BigDecimal.valueOf(doubleValue);
    	if (object instanceof BigInteger bigValue)
    		return BigDecimal.valueOf(bigValue.doubleValue());
    	if (object instanceof BigDecimal bigValue)
    		return bigValue;
    	throw new IllegalArgumentException("Class not convert to big decimal " + object.getClass().getName());
	}

    private OffsetDateTime convertToOffsetDateTime(Object object) {
    	if (object instanceof OffsetDateTime dateValue)
    		return dateValue;
    	if (object instanceof LocalDateTime dateValue)
    		return dateValue.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    	throw new IllegalArgumentException("Class not convert to OffsetDateTime " + object.getClass().getName());
	}

    @Override
    public RtNotifyContext read() {
		log.info("Start read rt notify item");
    	if (!toBeNotifyList.isEmpty())
    		return toBeNotifyList.remove(0);
        log.info("Nessun altra ricevuta da notifica");
        return null;
    }
}
