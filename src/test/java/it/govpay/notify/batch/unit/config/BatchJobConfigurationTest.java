package it.govpay.notify.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.config.BatchJobConfiguration;
import it.govpay.notify.batch.config.BatchProperties;
import it.govpay.notify.batch.listener.BatchExecutionRecapListener;
import it.govpay.notify.batch.tasklet.RtNotifyProcessor;
import it.govpay.notify.batch.tasklet.RtNotifyReader;
import it.govpay.notify.batch.tasklet.RtNotifyWriter;

@DisplayName("BatchJobConfiguration")
class BatchJobConfigurationTest {

    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private BatchProperties batchProperties;
    private BatchJobConfiguration config;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        batchProperties = mock(BatchProperties.class);
        when(batchProperties.getNotifyChunkSize()).thenReturn(10);

        config = new BatchJobConfiguration(jobRepository, transactionManager, batchProperties);
    }

    @Test
    @DisplayName("rtNotifyJob bean is built with the configured name")
    void rtNotifyJobIsConfigured() {
        Step step = mock(Step.class);
        BatchExecutionRecapListener listener = mock(BatchExecutionRecapListener.class);

        Job job = config.rtNotifyJob(step, listener);

        assertNotNull(job);
        assertEquals(Costanti.RT_NOTIFY_JOB_NAME, job.getName());
    }

    @Test
    @DisplayName("rtNotifyStep bean is built with reader/processor/writer")
    void rtNotifyStepIsConfigured() {
        RtNotifyReader reader = mock(RtNotifyReader.class);
        RtNotifyProcessor processor = mock(RtNotifyProcessor.class);
        RtNotifyWriter writer = mock(RtNotifyWriter.class);

        Step step = config.rtNotifyStep(reader, processor, writer);

        assertNotNull(step);
        assertEquals("rtNotifyStep", step.getName());
    }
}
