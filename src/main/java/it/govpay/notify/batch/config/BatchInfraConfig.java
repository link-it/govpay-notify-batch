package it.govpay.notify.batch.config;

import java.time.ZoneId;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;

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
            JobExplorer jobExplorer,
            JobRepository jobRepository,
            @Value("${govpay.batch.stale-threshold-minutes:120}") int staleThresholdMinutes) {
        return new JobConcurrencyService(jobExplorer, jobRepository, staleThresholdMinutes);
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(
            JobLauncher jobLauncher,
            JobConcurrencyService jobConcurrencyService,
            @Value("${govpay.batch.cluster-id:GovPay-Notify-Batch}") String clusterId,
            ZoneId applicationZoneId) {
        return new JobExecutionHelper(jobLauncher, jobConcurrencyService, clusterId, applicationZoneId);
    }
}
