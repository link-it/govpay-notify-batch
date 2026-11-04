package it.govpay.notify.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;
import it.govpay.notify.batch.config.ScheduledJobRunner;
import it.govpay.notify.batch.config.TestScheduledJobRunnerConfig;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import it.govpay.notify.batch.rt.config.RtSendScheduledJobRunner;

/**
 * Verifica end-to-end dell'esposizione delle metriche Prometheus:
 * <ul>
 *   <li>lo scrape {@code GET /actuator/prometheus} risponde in formato
 *       testuale Prometheus, con il tag comune {@code application};</li>
 *   <li>l'esecuzione del job pubblica le metriche standard di Spring Batch
 *       ({@code spring_batch_job}) e quelle di govpay-common
 *       ({@code govpay_batch_*}), con il tag {@code nodo} uguale al cluster-id;</li>
 *   <li>l'intervallo dello schedule e' pubblicato per entrambi i job:
 *       {@code fixedDelay} per {@code rtNotifyJob}, {@code cron} per
 *       {@code rtSendJob}.</li>
 * </ul>
 *
 * <p>Il servizio non ha una porta management separata: essendo l'unico
 * server web presente quello dell'actuator, scrape e health rispondono sulla
 * stessa porta (qui random per il test).
 */
@SpringBootTest(classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestScheduledJobRunnerConfig.class, PrometheusScrapeIntegrationTest.RtSendRunnerConfig.class})
@ActiveProfiles("integration")
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.task.scheduling.pool.size=1",
        // application.properties imposta initialDelayString=1 (1ms): senza questo
        // override lo @Scheduled reale lancia rtNotifyJob quasi subito, in corsa
        // con la chiamata esplicita del test sullo stesso job.
        "scheduler.initialDelayString=3600000",
        // Il runner di rtSendJob e' registrato da RtSendRunnerConfig solo per
        // pubblicarne l'intervallo: con il cron di default (ogni minuto) il job
        // potrebbe partire durante il test, quindi qui scatta solo il 1 gennaio.
        "scheduler.rtSendJob.cron=0 0 0 1 1 *",
        "govpay.batch.cluster-id=" + PrometheusScrapeIntegrationTest.NODO
})
class PrometheusScrapeIntegrationTest {

    static final String NODO = "nodo-test";

    /**
     * {@link RtSendScheduledJobRunner} ha {@code @Profile("default")} ed e'
     * condizionato a {@code govpay.batch.rt-send.enabled}: qui lo si registra
     * esplicitamente per verificare l'intervallo di tipo {@code cron}.
     */
    @TestConfiguration
    static class RtSendRunnerConfig {

        @Bean
        RtSendScheduledJobRunner rtSendScheduledJobRunner(
                JobExecutionHelper jobExecutionHelper,
                @Qualifier("rtSendJob") Job rtSendJob) {
            return new RtSendScheduledJobRunner(jobExecutionHelper, rtSendJob);
        }
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private ScheduledJobRunner batchScheduler;

    @MockitoBean
    private JobConcurrencyService jobConcurrencyService = mock(JobConcurrencyService.class);
    @MockitoBean
    private RendicontazioniRepository rendicontazioniRepository = mock(RendicontazioniRepository.class);

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void scrapeReturnsPrometheusFormatWithApplicationTag() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/plain");
        assertThat(response.body()).contains("# TYPE jvm_memory_used_bytes gauge");
        assertThat(response.body()).contains("application=\"govpay-notify-batch\"");
    }

    @Test
    void batchJobExecutionProducesSpringBatchMetrics() throws Exception {
        // Job "a vuoto": nessuna ricevuta da notificare. Basta a far girare il
        // job (anche se termina senza item) e a produrre le metriche standard
        // spring_batch_job/step di Micrometer.
        when(jobConcurrencyService.getCurrentRunningJobExecution(any())).thenReturn(null);
        when(rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                .thenReturn(List.of());

        JobExecution execution = batchScheduler.runBatchRtNotifyJob();
        assertThat(execution).isNotNull();

        String scrape = get("/actuator/prometheus").body();
        assertThat(scrape).contains("spring_batch_job_seconds_count");
        assertThat(scrape).contains("spring_batch_job_name=\"rtNotifyJob\"");
    }

    @Test
    void batchJobExecutionProducesGovpayBatchMetrics() throws Exception {
        when(jobConcurrencyService.getCurrentRunningJobExecution(any())).thenReturn(null);
        when(rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                .thenReturn(List.of());

        JobExecution execution = batchScheduler.runBatchRtNotifyJob();
        assertThat(execution).isNotNull();
        // Senza incrementer sul job i parametri dell'helper arrivano all'esecuzione (govpay-common#39)
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_CLUSTER_ID)).isEqualTo(NODO);
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_TRIGGER_TYPE)).isEqualTo("SCHEDULED");
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_CORRELATION_ID)).isNotBlank();

        String scrape = get("/actuator/prometheus").body();
        String nodo = "nodo=\"" + NODO + "\"";
        assertThat(righe(scrape, "govpay_batch_job_seconds_count{"))
                .anyMatch(r -> r.contains("job_name=\"rtNotifyJob\"") && r.contains("trigger=\"SCHEDULED\"")
                        && r.contains("esito=\"COMPLETED\"") && r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_schedule_ticks_total{"))
                .anyMatch(r -> r.contains("job_name=\"rtNotifyJob\"") && r.contains("esito_tick=\"CAN_PROCEED\"")
                        && r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_stale_threshold_seconds{")).anyMatch(r -> r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_job_last_success_timestamp_seconds{"))
                .anyMatch(r -> r.contains("job_name=\"rtNotifyJob\"") && r.contains(nodo));
    }

    @Test
    void scheduleIntervalIsPublishedForBothJobs() throws Exception {
        String scrape = get("/actuator/prometheus").body();
        String nodo = "nodo=\"" + NODO + "\"";
        List<String> intervalli = righe(scrape, "govpay_batch_schedule_interval_seconds{");

        // rtNotifyJob: fixedDelay di 2 ore (scheduler.rtNotifyJob.fixedDelayString=7200000)
        assertThat(intervalli)
                .anyMatch(r -> r.contains("job_name=\"rtNotifyJob\"") && r.contains("tipo=\"fixedDelay\"")
                        && r.contains(nodo) && r.endsWith(" 7200.0"));
        // rtSendJob: cron, intervallo = distanza tra le due prossime esecuzioni.
        // Dipende dall'istante del calcolo, quindi si verifica solo che sia positivo.
        assertThat(intervalli)
                .filteredOn(r -> r.contains("job_name=\"rtSendJob\""))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r).contains("tipo=\"cron\"").contains(nodo);
                    assertThat(Double.parseDouble(r.substring(r.lastIndexOf(' ') + 1))).isPositive();
                });
    }

    private static List<String> righe(String scrape, String prefisso) {
        return scrape.lines().filter(r -> r.startsWith(prefisso)).toList();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
