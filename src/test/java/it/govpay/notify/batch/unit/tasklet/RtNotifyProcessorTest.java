package it.govpay.notify.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import it.govpay.notify.batch.dto.RtNotifyBatch;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.service.EnteApiService;
import it.govpay.notify.batch.tasklet.RtNotifyProcessor;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtNotifyProcessor")
class RtNotifyProcessorTest {

    @Mock
    private EnteApiService enteApiService;

    private RtNotifyProcessor processor;
    private RtNotifyContext context;

    private static final Long RT_ID = 1L;
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";

    @BeforeEach
    void setUp() {
        processor = new RtNotifyProcessor(enteApiService);

        context = RtNotifyContext.builder()
                .rtId(RT_ID)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();
    }

    @Nested
    @DisplayName("process")
    class ProcessTest {

        @Test
        @DisplayName("should return batch with OK esito when service succeeds")
        void shouldReturnBatchWithOkEsitoWhenSuccess() throws Exception {
            doAnswer(invocation -> {
                invocation.<CompletableFuture<HttpStatusCode>>getArgument(1).complete(HttpStatus.OK);
                return null;
            }).when(enteApiService).notifyRendicontazione(eq(context), any(CompletableFuture.class));

            RtNotifyBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(RT_ID, result.getRtId());
            assertEquals("OK", result.getEsito());
            assertNotNull(result.getNotifyTime());
            assertNull(result.getMessage());
        }

        @Test
        @DisplayName("should return batch with KO esito when service returns error message")
        void shouldReturnBatchWithKoEsitoWhenServiceFails() throws Exception {
            when(enteApiService.notifyRendicontazione(eq(context), any(CompletableFuture.class)))
                    .thenReturn("Notifica errata");

            RtNotifyBatch result = processor.process(context);

            assertNotNull(result);
            assertEquals(RT_ID, result.getRtId());
            assertEquals("KO", result.getEsito());
            assertEquals("Notifica errata", result.getMessage());
            assertNotNull(result.getNotifyTime());
        }

        @Test
        @DisplayName("should always return a batch (never null)")
        void shouldAlwaysReturnBatch() throws Exception {
            doAnswer(invocation -> {
                invocation.<CompletableFuture<HttpStatusCode>>getArgument(1).complete(HttpStatus.OK);
                return null;
            }).when(enteApiService).notifyRendicontazione(eq(context), any(CompletableFuture.class));

            RtNotifyBatch result = processor.process(context);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should propagate exception when service throws")
        void shouldPropagateExceptionWhenServiceThrows() throws Exception {
            when(enteApiService.notifyRendicontazione(eq(context), any(CompletableFuture.class)))
                    .thenThrow(new RuntimeException("Connection error"));

            assertThrows(RuntimeException.class, () -> processor.process(context));
        }
    }
}
