package it.govpay.notify.batch.config;

import java.time.ZoneId;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;
import jakarta.persistence.EntityManager;

/**
 * Dichiarazione esplicita dei bean infrastrutturali per la gestione batch
 * multi-nodo forniti da {@code govpay-common}. {@link JobExecutionHelper} e
 * {@link JobConcurrencyService} non sono registrati come bean da auto-configurazione
 * della libreria ({@code BatchCommonAutoConfiguration} espone solo factory statiche),
 * quindi vanno cablati qui altrimenti gli {@code AbstractScheduledJobRunner} /
 * {@code AbstractCronJobRunner} dei consumer falliscono lo startup con
 * "No qualifying bean of type 'JobExecutionHelper' available". Allineato a
 * {@code BatchInfraConfig} di {@code govpay-rt-batch}.
 */
@Configuration
public class BatchInfraConfig {

    @Bean
    public JobConcurrencyService jobConcurrencyService(
            JobRepository jobRepository,
            @Value("${govpay.batch.stale-threshold-minutes:120}") int staleThresholdMinutes) {
        return new JobConcurrencyService(jobRepository, staleThresholdMinutes);
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(
            JobOperator jobOperator,
            JobConcurrencyService jobConcurrencyService,
            @Value("${govpay.batch.cluster-id:GovPay-Notify-Batch}") String clusterId,
            ZoneId applicationZoneId) {
        return new JobExecutionHelper(jobOperator, jobConcurrencyService, clusterId, applicationZoneId);
    }

    /**
     * Raggruppa i collaboratori richiesti da {@code AbstractBatchController}
     * (vedi {@link BatchControllerSupport}).
     */
    @Bean
    public BatchControllerSupport batchControllerSupport(
            JobExecutionHelper jobExecutionHelper,
            JobRepository jobRepository,
            Environment environment,
            ZoneId applicationZoneId,
            @Value("${scheduler.rtNotifyJob.fixedDelayString:7200000}") long schedulerIntervalMillis,
            EntityManager entityManager) {
        return new BatchControllerSupport(jobExecutionHelper, jobRepository, environment,
                applicationZoneId, schedulerIntervalMillis, entityManager);
    }
}
