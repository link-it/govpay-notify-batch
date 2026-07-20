package it.govpay.notify.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import it.govpay.common.batch.runner.JobExecutionHelper;

/**
 * Configurazione di test che fornisce il bean ScheduledJobRunner per il profilo "integration".
 * <p>
 * Necessaria perché ScheduledJobRunner ha @Profile("default") e quindi non viene
 * creato automaticamente quando i test usano @ActiveProfiles("integration").
 */
@TestConfiguration
public class TestScheduledJobRunnerConfig {

    @Bean
    public ScheduledJobRunner scheduledJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtNotifyJob") Job rtNotifyJob) {
        return new ScheduledJobRunner(jobExecutionHelper, rtNotifyJob);
    }
}
