package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.TriggerType;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionCheckResult;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionResult;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.rt.config.RtSendScheduledJobRunner;

@DisplayName("RtSendScheduledJobRunner")
class RtSendScheduledJobRunnerTest {

    private JobExecutionHelper helper;
    private Job rtSendJob;
    private RtSendScheduledJobRunner runner;

    @BeforeEach
    void setUp() {
        helper = mock(JobExecutionHelper.class);
        rtSendJob = mock(Job.class);
        runner = new RtSendScheduledJobRunner(helper, rtSendJob);
    }

    @Test
    @DisplayName("il costruttore cabla rtSendJob e il nome job della spedizione RT")
    void constructorWiresFields() {
        assertSame(rtSendJob, ReflectionTestUtils.invokeMethod(runner, "getJob"));
        assertEquals(Costanti.RT_SEND_JOB_NAME, ReflectionTestUtils.invokeMethod(runner, "getJobName"));
        assertSame(helper, ReflectionTestUtils.invokeMethod(runner, "getJobExecutionHelper"));
    }

    @Test
    @DisplayName("nodo libero -> il job viene avviato come esecuzione SCHEDULED")
    void launchesJobWhenCheckAllowsIt() throws Exception {
        JobExecution execution = mock(JobExecution.class);
        when(helper.checkBeforeExecution(Costanti.RT_SEND_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.CAN_PROCEED, null, null));
        when(helper.runJob(rtSendJob, Costanti.RT_SEND_JOB_NAME, TriggerType.SCHEDULED)).thenReturn(execution);

        assertSame(execution, runner.runRtSendJob());
    }

    @Test
    @DisplayName("job gia' in corso su un altro nodo -> nessun avvio, niente sovrapposizioni")
    void skipsWhenAlreadyRunningElsewhere() throws Exception {
        when(helper.checkBeforeExecution(Costanti.RT_SEND_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_OTHER_NODE, null, "altro-nodo"));

        assertNull(runner.runRtSendJob());

        verify(helper, never()).runJob(rtSendJob, Costanti.RT_SEND_JOB_NAME, TriggerType.SCHEDULED);
    }
}
