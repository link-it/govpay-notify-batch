package it.govpay.notify.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.env.Environment;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;
import it.govpay.notify.batch.config.BatchControllerSupport;
import it.govpay.notify.batch.config.BatchInfraConfig;
import jakarta.persistence.EntityManager;

/**
 * I bean di {@link BatchInfraConfig} sono il cablaggio esplicito degli helper
 * multi-nodo di govpay-common, che l'auto-configurazione della libreria non
 * registra: se uno di questi metodi smette di produrre un bean valido, lo
 * startup dell'applicazione fallisce con "No qualifying bean of type ...".
 */
@DisplayName("BatchInfraConfig")
class BatchInfraConfigTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    private final BatchInfraConfig config = new BatchInfraConfig();

    @Test
    @DisplayName("jobConcurrencyService viene creato con il JobRepository fornito")
    void jobConcurrencyServiceIsCreated() {
        JobConcurrencyService service = config.jobConcurrencyService(mock(JobRepository.class), 120);

        assertNotNull(service);
    }

    @Test
    @DisplayName("jobExecutionHelper viene creato con il clusterId configurato")
    void jobExecutionHelperIsCreated() {
        JobExecutionHelper helper = config.jobExecutionHelper(mock(JobOperator.class),
                mock(JobConcurrencyService.class), "GovPay-Notify-Batch", ZONE);

        assertNotNull(helper);
        assertEquals("GovPay-Notify-Batch", helper.getClusterId());
    }

    @Test
    @DisplayName("batchControllerSupport raccoglie i collaboratori richiesti da AbstractBatchController")
    void batchControllerSupportCarriesAllCollaborators() {
        JobExecutionHelper helper = mock(JobExecutionHelper.class);
        JobRepository jobRepository = mock(JobRepository.class);
        Environment environment = mock(Environment.class);
        EntityManager entityManager = mock(EntityManager.class);

        BatchControllerSupport support = config.batchControllerSupport(
                helper, jobRepository, environment, ZONE, 7_200_000L, entityManager);

        assertSame(helper, support.jobExecutionHelper());
        assertSame(jobRepository, support.jobRepository());
        assertSame(environment, support.environment());
        assertSame(ZONE, support.applicationZoneId());
        assertEquals(7_200_000L, support.schedulerIntervalMillis());
        assertSame(entityManager, support.entityManager());
    }
}
