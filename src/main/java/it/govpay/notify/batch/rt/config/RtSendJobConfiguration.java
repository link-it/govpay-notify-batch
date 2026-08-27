package it.govpay.notify.batch.rt.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.rt.dto.NotificaContext;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import it.govpay.notify.batch.rt.tasklet.RtSendProcessor;
import it.govpay.notify.batch.rt.tasklet.RtSendReader;
import it.govpay.notify.batch.rt.tasklet.RtSendWriter;

/**
 * Configurazione del job di spedizione delle notifiche RT.
 * Job indipendente da {@code rtNotifyJob}: legge dalla tabella
 * {@code notifiche} (tipo RICEVUTA, stato DA_SPEDIRE) e spedisce
 * tramite il client OpenAPI {@code govpay-ec-client} v2.
 */
@Configuration
public class RtSendJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final int chunkSize;

    public RtSendJobConfiguration(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Value("${govpay.batch.rt-send.chunk-size:50}") int chunkSize) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.chunkSize = chunkSize;
    }

    @Bean
    public Job rtSendJob(Step rtSendStep) {
        return new JobBuilder(Costanti.RT_SEND_JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(rtSendStep)
                .build();
    }

    @Bean
    public Step rtSendStep(
            RtSendReader rtSendReader,
            RtSendProcessor rtSendProcessor,
            RtSendWriter rtSendWriter) {
        // chunk(size).transactionManager(tm): la variante chunk(size, tm) e' deprecata
        // per la rimozione in Spring Batch 6.
        return new StepBuilder("rtSendStep", jobRepository)
                .<NotificaContext, RtSendResult>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(rtSendReader)
                .processor(rtSendProcessor)
                .writer(rtSendWriter)
                .build();
    }
}
