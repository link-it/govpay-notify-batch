package it.govpay.notify.batch.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import it.govpay.notify.batch.config.BatchControllerSupport;
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
    private final Job rtSendJob;
    private final boolean rtSendEnabled;
    private final EnteApiService enteApiService;

    public BatchController(
            BatchControllerSupport support,
            @Qualifier("rtNotifyJob") Job rtNotifyJob,
            @Qualifier("rtSendJob") Job rtSendJob,
            EnteApiService enteApiService,
            @Value("${govpay.batch.rt-send.enabled:false}") boolean rtSendEnabled) {
        super(support.jobExecutionHelper(), support.jobRepository(), support.environment(),
                support.applicationZoneId(), support.schedulerIntervalMillis(), support.entityManager());
        this.rtNotifyJob = rtNotifyJob;
        this.rtSendJob = rtSendJob;
        this.rtSendEnabled = rtSendEnabled;
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
    protected String getDisplayName() {
        return "Notifica ricevute (RT) all'Ente Creditore";
    }

    @Override
    protected String getDescription() {
        return "Individua le rendicontazioni acquisite ma non ancora notificate all'applicazione dell'Ente "
                + "Creditore e le invia tramite chiamata REST verso l'endpoint configurato sul connettore.";
    }

    @Override
    protected ResponseEntity<String> clearCache() {
    	enteApiService.clearCache();
        return ResponseEntity.ok("Cache connettori svuotata");
    }

    /**
     * Esegue manualmente i job batch del progetto.
     * <p>
     * Primo job: {@code rtNotifyJob} (notifica delle rendicontazioni), gestito dalla
     * superclasse — risponde con {@code 202 Accepted} se libero, {@code 409 Conflict}
     * se gia' in esecuzione (vedi {@link AbstractBatchController#eseguiJob}).
     * <p>
     * Secondo job: {@code rtSendJob} (spedizione delle ricevute di pagamento), lanciato
     * "best effort" in modo asincrono dopo il primo: il pre-check di concorrenza e' fatto
     * da {@link JobExecutionHelper#executeIfPossible} (se gia' in esecuzione viene skippato,
     * si trova solo nel log). Il suo esito non altera la response HTTP, che resta quella
     * del primo job.
     */
    @GetMapping("/run")
    public ResponseEntity<Object> eseguiJobEndpoint(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        ResponseEntity<Object> notifyResponse = eseguiJob(force);
        lanciaRtSendJobAsincrono();
        return notifyResponse;
    }

    /**
     * Lancia {@code rtSendJob} in background tramite
     * {@link JobExecutionHelper#executeIfPossible}, che fa internamente il check di
     * concorrenza multi-nodo. Eventuali eccezioni sono solo loggate: la risposta HTTP
     * dell'endpoint resta quella del job primario.
     * <p>
     * Rispetta la feature flag {@code govpay.batch.rt-send.enabled}: se {@code false},
     * il job non viene lanciato (kill switch coerente con il runner schedulato, che
     * tramite {@code @ConditionalOnProperty} non viene nemmeno istanziato).
     */
    private void lanciaRtSendJobAsincrono() {
        if (!rtSendEnabled) {
            log.debug("Spedizione RT disabilitata (govpay.batch.rt-send.enabled=false): {} non avviato",
                    Costanti.RT_SEND_JOB_NAME);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Lancio best-effort del job {}", Costanti.RT_SEND_JOB_NAME);
                getJobExecutionHelper().executeIfPossible(rtSendJob, Costanti.RT_SEND_JOB_NAME);
            } catch (Exception e) {
                log.warn("Impossibile avviare {} dal controller: {}",
                        Costanti.RT_SEND_JOB_NAME, e.getMessage(), e);
            }
        });
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
