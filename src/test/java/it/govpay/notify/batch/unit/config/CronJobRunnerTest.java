package it.govpay.notify.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.config.CronJobRunner;

@DisplayName("CronJobRunner")
class CronJobRunnerTest {

    @Test
    @DisplayName("constructor wires job and job name correctly")
    void constructorWiresFields() {
        JobExecutionHelper helper = mock(JobExecutionHelper.class);
        Job job = mock(Job.class);

        CronJobRunner runner = new CronJobRunner(helper, job);

        assertNotNull(runner);
        assertSame(job, ReflectionTestUtils.invokeMethod(runner, "getJob"));
        assertEquals(Costanti.RT_NOTIFY_JOB_NAME, ReflectionTestUtils.invokeMethod(runner, "getJobName"));
        assertSame(helper, ReflectionTestUtils.invokeMethod(runner, "getJobExecutionHelper"));
    }
}
