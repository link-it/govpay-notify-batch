package it.govpay.notify.batch.rt.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractScheduledJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;

/**
 * Runner per l'esecuzione schedulata del job di spedizione RT in modalita'
 * multi-nodo. Attivo solo con profile "default" (non "cron") e quando
 * {@code govpay.batch.rt-send.enabled=true}.
 */
@Component
@Profile("default")
@ConditionalOnProperty(name = "govpay.batch.rt-send.enabled", havingValue = "true", matchIfMissing = false)
public class RtSendScheduledJobRunner extends AbstractScheduledJobRunner {

    public RtSendScheduledJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtSendJob") Job rtSendJob) {
        super(jobExecutionHelper, rtSendJob, Costanti.RT_SEND_JOB_NAME);
    }

    /**
     * Schedulazione cron, default {@code 0 * * * * *} (ogni minuto al secondo 0)
     * per replicare il comportamento del monolite GovPay
     * (cfr. {@code applicationContext-timers.xml}: {@code <task:scheduled
     * cron="0 * * * * ?"/>} per la spedizione delle notifiche).
     * <p>
     * Se una run precedente e' ancora in corso (su questo o altro nodo), il
     * dispatcher {@link it.govpay.common.batch.runner.JobExecutionHelper}
     * salta l'esecuzione: non si verificano sovrapposizioni.
     */
    @Scheduled(cron = "${scheduler.rtSendJob.cron:0 * * * * *}")
    public JobExecution runRtSendJob() throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, InvalidJobParametersException {
        return executeScheduledJob();
    }
}
