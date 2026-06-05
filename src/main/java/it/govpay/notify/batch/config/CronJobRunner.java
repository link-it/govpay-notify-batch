package it.govpay.notify.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractCronJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;

/**
 * Runner per l'esecuzione da command line del job RT notify in modalita' multi-nodo.
 * <p>
 * Attivo solo con profile "cron" (non "default").
 */
@Component
@Profile("cron")
public class CronJobRunner extends AbstractCronJobRunner {

    public CronJobRunner(
            JobExecutionHelper jobExecutionHelper,
            @Qualifier("rtNotifyJob") Job rtNotifyJob) {
        super(jobExecutionHelper, rtNotifyJob, Costanti.RT_NOTIFY_JOB_NAME);
    }
}
