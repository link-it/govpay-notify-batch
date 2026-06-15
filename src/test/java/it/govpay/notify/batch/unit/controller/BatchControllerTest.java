package it.govpay.notify.batch.unit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.controller.BatchController;
import it.govpay.notify.batch.service.EnteApiService;

@DisplayName("BatchController")
class BatchControllerTest {

    private JobExecutionHelper jobExecutionHelper;
    private JobRepository jobRepository;
    private Job rtNotifyJob;
    private Job rtSendJob;
    private EnteApiService enteApiService;
    private Environment environment;
    private ZoneId applicationZoneId;

    private BatchController controller;

    @BeforeEach
    void setUp() {
        jobExecutionHelper = mock(JobExecutionHelper.class);
        jobRepository = mock(JobRepository.class);
        rtNotifyJob = mock(Job.class);
        rtSendJob = mock(Job.class);
        enteApiService = mock(EnteApiService.class);
        environment = mock(Environment.class);
        applicationZoneId = ZoneId.of("Europe/Rome");

        controller = buildController(true);
    }

    private BatchController buildController(boolean rtSendEnabled) {
        return new BatchController(
                jobExecutionHelper,
                jobRepository,
                rtNotifyJob,
                rtSendJob,
                enteApiService,
                environment,
                applicationZoneId,
                3_600_000L,
                rtSendEnabled);
    }

    @Test
    @DisplayName("getJob returns the injected rtNotifyJob")
    void getJobReturnsInjected() {
        Job result = ReflectionTestUtils.invokeMethod(controller, "getJob");
        assertSame(rtNotifyJob, result);
    }

    @Test
    @DisplayName("getJobName returns RT_NOTIFY_JOB_NAME constant")
    void getJobNameReturnsConstant() {
        String result = ReflectionTestUtils.invokeMethod(controller, "getJobName");
        assertEquals(Costanti.RT_NOTIFY_JOB_NAME, result);
    }

    @Test
    @DisplayName("clearCache delegates to EnteApiService and returns 200 OK")
    void clearCacheDelegatesAndReturnsOk() {
        ResponseEntity<String> response = ReflectionTestUtils.invokeMethod(controller, "clearCache");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Cache connettori svuotata", response.getBody());
        verify(enteApiService).clearCache();
    }

    @Test
    @DisplayName("clearCacheEndpoint (inherited) delegates to clearCache")
    void clearCacheEndpointDelegates() {
        ResponseEntity<String> response = controller.clearCacheEndpoint();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(enteApiService).clearCache();
    }

    @Test
    @DisplayName("eseguiJobEndpoint returns a non-null response (force=false)")
    void eseguiJobEndpointReturnsResponse() {
        assertNotNull(controller.eseguiJobEndpoint(false));
    }

    @Test
    @DisplayName("eseguiJobEndpoint returns a non-null response (force=true)")
    void eseguiJobEndpointForceReturnsResponse() {
        assertNotNull(controller.eseguiJobEndpoint(true));
    }

    @Test
    @DisplayName("eseguiJobEndpoint avvia anche rtSendJob in modalita' best-effort (flag abilitato)")
    void eseguiJobEndpointAlsoTriggersRtSendJob() throws Exception {
        controller.eseguiJobEndpoint(false);

        // executeIfPossible(rtSendJob, "rtSendJob") gira async sul ForkJoinPool.commonPool().
        // Aspettiamo che la chiamata venga registrata (max ~2s) per evitare flakiness.
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() ->
                        verify(jobExecutionHelper).executeIfPossible(rtSendJob, Costanti.RT_SEND_JOB_NAME));
    }

    @Test
    @DisplayName("eseguiJobEndpoint NON avvia rtSendJob se govpay.batch.rt-send.enabled=false")
    void eseguiJobEndpointSkipsRtSendJobWhenDisabled() throws Exception {
        BatchController disabledController = buildController(false);

        disabledController.eseguiJobEndpoint(false);

        // Diamo tempo a un eventuale runAsync di partire (max ~1s) per essere sicuri che NON parta.
        Thread.sleep(500);
        org.mockito.Mockito.verify(jobExecutionHelper, org.mockito.Mockito.never())
                .executeIfPossible(rtSendJob, Costanti.RT_SEND_JOB_NAME);
    }

    @Test
    @DisplayName("getLastExecutionEndpoint returns a non-null response")
    void getLastExecutionEndpointReturnsResponse() {
        assertNotNull(controller.getLastExecutionEndpoint());
    }

    @Test
    @DisplayName("getNextExecutionEndpoint returns a non-null response")
    void getNextExecutionEndpointReturnsResponse() {
        assertNotNull(controller.getNextExecutionEndpoint());
    }
}
