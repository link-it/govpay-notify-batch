package it.govpay.notify.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import it.govpay.notify.batch.dto.RtNotifyBatch;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import it.govpay.notify.batch.tasklet.RtNotifyWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtNotifyWriter")
class RtNotifyWriterTest {

    @Mock
    private RendicontazioniRepository rendicontazioniRepository;

    private RtNotifyWriter writer;

    @BeforeEach
    void setUp() {
        writer = new RtNotifyWriter(rendicontazioniRepository);
    }

    @Nested
    @DisplayName("write")
    class WriteTest {

        @Test
        @DisplayName("should call registerNotificaRt only for OK items")
        void shouldCallRegisterNotificaRtForEachItem() throws Exception {
            RtNotifyBatch batch1 = RtNotifyBatch.builder().rtId(10L).esito("OK").build();
            RtNotifyBatch batch2 = RtNotifyBatch.builder().rtId(20L).esito("KO").message("error").build();

            writer.write(new Chunk<>(Arrays.asList(batch1, batch2)));

            verify(rendicontazioniRepository).registerNotificaRt(10L);
            verify(rendicontazioniRepository, never()).registerNotificaRt(20L);
        }

        @Test
        @DisplayName("should not register KO items")
        void shouldNotRegisterKoItems() throws Exception {
            RtNotifyBatch batch = RtNotifyBatch.builder()
                    .rtId(10L)
                    .esito("KO")
                    .message("Non autorizzato")
                    .build();

            writer.write(new Chunk<>(Arrays.asList(batch)));

            verifyNoInteractions(rendicontazioniRepository);
        }

        @Test
        @DisplayName("should skip null items")
        void shouldSkipNullItems() throws Exception {
            RtNotifyBatch batch = RtNotifyBatch.builder().rtId(10L).esito("OK").build();

            assertDoesNotThrow(() -> writer.write(new Chunk<>(Arrays.asList(batch, null))));

            verify(rendicontazioniRepository).registerNotificaRt(10L);
            verifyNoMoreInteractions(rendicontazioniRepository);
        }

        @Test
        @DisplayName("should handle empty chunk without exceptions")
        void shouldHandleEmptyChunk() throws Exception {
            assertDoesNotThrow(() -> writer.write(new Chunk<>(Collections.emptyList())));
            verifyNoInteractions(rendicontazioniRepository);
        }
    }
}
