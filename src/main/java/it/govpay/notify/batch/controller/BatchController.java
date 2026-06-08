package it.govpay.notify.batch.controller;

import java.time.ZoneId;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.batch.controller.AbstractBatchController;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.service.EnteApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST per l'esecuzione manuale e il monitoraggio dei job batch.
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
public class BatchController extends AbstractBatchController {

    private final Job rtNotifyJob;
    private final EnteApiService enteApiService;

    public BatchController(
            JobExecutionHelper jobExecutionHelper,
            JobRepository jobRepository,
            @Qualifier("rtNotifyJob") Job rtNotifyJob,
            EnteApiService enteApiService,
            Environment environment,
            ZoneId applicationZoneId,
            @Value("${scheduler.rtNotifyJob.fixedDelayString:7200000}") long schedulerIntervalMillis) {
        super(jobExecutionHelper, jobRepository, environment, applicationZoneId, schedulerIntervalMillis);
        this.rtNotifyJob = rtNotifyJob;
        this.enteApiService = enteApiService;
    }

    @Override
    protected Job getJob() {
        return rtNotifyJob;
    }

    @Override
    protected String getJobName() {
        return Costanti.RT_NOTIFY_JOB_NAME;
    }

    @Override
    protected ResponseEntity<String> clearCache() {
    	enteApiService.clearCache();
        return ResponseEntity.ok("Cache connettori svuotata");
    }

    @GetMapping("/run")
    public ResponseEntity<Object> eseguiJobEndpoint(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        return eseguiJob(force);
    }

    @GetMapping("/status")
    public ResponseEntity<BatchStatusInfo> getStatusEndpoint() {
        return getStatus();
    }

    @GetMapping("/lastExecution")
    public ResponseEntity<LastExecutionInfo> getLastExecutionEndpoint() {
        return getLastExecution();
    }

    @GetMapping("/nextExecution")
    public ResponseEntity<NextExecutionInfo> getNextExecutionEndpoint() {
        return getNextExecution();
    }
}
