package it.govpay.notify.batch.listener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Listener che stampa un riepilogo dettagliato dell'esecuzione del batch.
 */
@Component
@Slf4j
public class BatchExecutionRecapListener implements JobExecutionListener {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Clock clock;

    public BatchExecutionRecapListener(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("INIZIO BATCH RT NOTIFY");
        log.info("Job ID: {}", jobExecution.getJobInstanceId());
        log.info("Avvio: {}", LocalDateTime.now(clock).format(TIME_FORMATTER));
        log.info("=".repeat(80));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("RIEPILOGO ESECUZIONE BATCH");
        log.info("=".repeat(80));

        log.info("Status finale: {}", jobExecution.getStatus());
        log.info("Durata totale: {} secondi", durataSecondi(jobExecution));
        log.info("");

        log.info("=".repeat(80));
    }

    /**
     * Durata dell'esecuzione in secondi. {@code JobExecution} espone i timestamp
     * come {@link LocalDateTime}, che non porta con se' il fuso: prima di calcolare
     * l'intervallo li si ancora al timezone applicativo e li si converte in
     * {@link Instant}, cosi' la durata resta corretta anche a cavallo del
     * cambio ora legale/solare.
     */
    private long durataSecondi(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return Duration.between(toInstant(startTime), toInstant(endTime)).getSeconds();
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(clock.getZone()).toInstant();
    }
}
