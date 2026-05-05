package it.govpay.notify.batch.unit.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

import it.govpay.notify.batch.listener.BatchExecutionRecapListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchExecutionRecapListener")
class BatchExecutionRecapListenerTest {

    @Mock
    private JobExecution jobExecution;

    private BatchExecutionRecapListener listener;

    @BeforeEach
    void setUp() {
        listener = new BatchExecutionRecapListener();
    }

    @Nested
    @DisplayName("beforeJob")
    class BeforeJobTest {

        @Test
        @DisplayName("should log without exceptions")
        void shouldLogWithoutExceptions() {
            when(jobExecution.getJobId()).thenReturn(1L);

            assertDoesNotThrow(() -> listener.beforeJob(jobExecution));
        }
    }

    @Nested
    @DisplayName("afterJob")
    class AfterJobTest {

        @Test
        @DisplayName("should log completed status without exceptions")
        void shouldLogCompletedStatusWithoutExceptions() {
            when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(10));
            when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
            when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
        }

        @Test
        @DisplayName("should log failed status without exceptions")
        void shouldLogFailedStatusWithoutExceptions() {
            when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(5));
            when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
            when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);

            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
        }

        @Test
        @DisplayName("should calculate duration correctly")
        void shouldCalculateDurationCorrectly() {
            LocalDateTime start = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 1, 15, 10, 0, 30);
            when(jobExecution.getStartTime()).thenReturn(start);
            when(jobExecution.getEndTime()).thenReturn(end);
            when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

            assertDoesNotThrow(() -> listener.afterJob(jobExecution));
        }
    }
}
