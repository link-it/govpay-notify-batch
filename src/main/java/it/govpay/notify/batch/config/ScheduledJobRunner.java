package it.govpay.notify.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractScheduledJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;

/**
 * Runner per l'esecuzione schedulata del job RT Notify in modalita' multi-nodo.
 * <p>
 * Attivo solo con profile "default" (non "cron").
 */
@Component
@Profile("default")
@EnableScheduling
public class ScheduledJobRunner extends AbstractScheduledJobRunner {

    public ScheduledJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtNotifyJob") Job rtNotifyJob) {
        super(jobExecutionHelper, rtNotifyJob, Costanti.RT_NOTIFY_JOB_NAME);
    }

    @Scheduled(
        fixedDelayString = "${scheduler.rtNotifyJob.fixedDelayString:7200000}",
        initialDelayString = "${scheduler.initialDelayString:1}"
    )
    public JobExecution runBatchRtNotifyJob() throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, InvalidJobParametersException {
        return executeScheduledJob();
    }
}
