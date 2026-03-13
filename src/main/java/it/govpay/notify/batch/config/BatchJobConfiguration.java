package it.govpay.notify.batch.config;

import lombok.extern.slf4j.Slf4j;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.dto.RtNotifyBatch;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.tasklet.RtNotifyProcessor;
import it.govpay.notify.batch.tasklet.RtNotifyReader;
import it.govpay.notify.batch.tasklet.RtNotifyWriter;

/**
 * Configuration for FDR Acquisition Batch Job
 */
@Configuration
@Slf4j
public class BatchJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;

    public BatchJobConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        BatchProperties batchProperties
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.batchProperties = batchProperties;
    }

    /**
     * Main RT Notify Job with 3 steps
     */
    @Bean
    public Job rtNotifyJob(
        Step rtNotifyStep,
        it.govpay.notify.batch.listener.BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder(Costanti.RT_NOTIFY_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(batchExecutionRecapListener)
            .start(rtNotifyStep)
            .build();
    }

    /**
     * Tasklet: RT Notify
     */
    @Bean
    public Step rtNotifyStep(
    	RtNotifyReader rtNotifyReader,
        RtNotifyProcessor rtNotifyProcessor,
        RtNotifyWriter rtNotifyWriter
    ) {
        return new StepBuilder("rtNotifyStep", jobRepository)
            .<RtNotifyContext, RtNotifyBatch>chunk(batchProperties.getNotifyChunkSize(), transactionManager)
            .reader(rtNotifyReader)
            .processor(rtNotifyProcessor)
            .writer(rtNotifyWriter)
            .listener(rtNotifyReader) // Register reader as step listener for queue reset
            .build();
    }

}
