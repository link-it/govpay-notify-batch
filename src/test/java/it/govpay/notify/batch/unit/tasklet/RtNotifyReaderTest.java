package it.govpay.notify.batch.unit.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import it.govpay.notify.batch.tasklet.RtNotifyReader;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtNotifyReader")
class RtNotifyReaderTest {

    @Mock
    private RendicontazioniRepository rndRepository;

    private static final int FINESTRA_TEMPORALE = 30;
    private static final String TAX_CODE_1 = "12345678901";
    private static final String TAX_CODE_2 = "98765432101";
    private static final String IUV_1 = "01234567890123456";
    private static final String IUV_2 = "65432109876543210";
    private static final String IUR_1 = "IUR123456";
    private static final String IUR_2 = "IUR654321";

    /** Builds a row matching the 18-column query result. */
    private Object[] buildRow(long id, String taxCode, String iuv, String iur) {
        return new Object[]{
            id,                          // [0]  r.id
            taxCode,                     // [1]  d.codDominio
            iuv,                         // [2]  r.iuv
            iur,                         // [3]  r.iur
            1,                           // [4]  r.indiceDati
            BigDecimal.valueOf(100.00),  // [5]  r.importoPagato
            0,                           // [6]  r.esito
            LocalDateTime.now(),         // [7]  r.data
            "FLUSSO001",                 // [8]  fr.codFlusso
            LocalDateTime.now(),         // [9]  fr.dataOraFlusso
            "IUR_FR",                    // [10] fr.iur
            LocalDateTime.now(),         // [11] fr.dataRegolamento
            LocalDateTime.now(),         // [12] fr.dataOraPubblicazione
            LocalDateTime.now(),         // [13] fr.dataOraAggiornamento
            "PSP001",                    // [14] fr.codPsp
            "BIC001",                    // [15] fr.codBicRiversamento
            1L,                          // [16] fr.revisione
            "APP001"                     // [17] a.codApplicazione
        };
    }

    @Nested
    @DisplayName("initToBeNotify")
    class InitToBeNotifyTest {

        @Test
        @DisplayName("should query repository with dataLimite")
        void shouldQueryRepositoryWithDataLimite() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            reader.initToBeNotify();

            verify(rndRepository).findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("should populate list with results from repository using Long ids")
        void shouldPopulateListWithResultsUsingLongIds() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            List<Object[]> results = new ArrayList<>();
            results.add(buildRow(1L, TAX_CODE_1, IUV_1, IUR_1));
            results.add(buildRow(2L, TAX_CODE_2, IUV_2, IUR_2));
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeNotify();

            RtNotifyContext first = reader.read();
            assertNotNull(first);
            assertEquals(1L, first.getRtId());
            assertEquals(TAX_CODE_1, first.getTaxCode());
            assertEquals(IUV_1, first.getIuv());
            assertEquals(IUR_1, first.getIur());
            assertEquals("APP001", first.getCodApplicazione());
            assertEquals("PSP001", first.getIdPsp());
            assertEquals("BIC001", first.getBicRiversamento());

            RtNotifyContext second = reader.read();
            assertNotNull(second);
            assertEquals(2L, second.getRtId());
        }

        @Test
        @DisplayName("should handle BigInteger ids from repository")
        void shouldHandleBigIntegerIdsFromRepository() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[0] = BigInteger.valueOf(999L);
            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeNotify();

            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertEquals(999L, result.getRtId());
        }

        @Test
        @DisplayName("should convert LocalDateTime fields to OffsetDateTime")
        void shouldConvertLocalDateTimeToOffsetDateTime() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            List<Object[]> results = new ArrayList<>();
            results.add(buildRow(1L, TAX_CODE_1, IUV_1, IUR_1));
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeNotify();

            RtNotifyContext result = reader.read();
            assertNotNull(result.getData());
            assertNotNull(result.getDataFlusso());
            assertNotNull(result.getDataRegolamento());
            assertNotNull(result.getDataOraPubblicazione());
            assertNotNull(result.getDataOraAggiornamento());
        }

        @Test
        @DisplayName("should accept OffsetDateTime fields directly")
        void shouldAcceptOffsetDateTimeFieldsDirectly() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            OffsetDateTime now = OffsetDateTime.now();
            row[7] = now;
            row[9] = now;

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeNotify();

            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertEquals(now, result.getData());
            assertEquals(now, result.getDataFlusso());
        }
    }

    @Nested
    @DisplayName("read")
    class ReadTest {

        @Test
        @DisplayName("should return items in order and null when exhausted")
        void shouldReturnItemsInOrderAndNullWhenExhausted() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            List<Object[]> results = new ArrayList<>();
            results.add(buildRow(1L, TAX_CODE_1, IUV_1, IUR_1));
            results.add(buildRow(2L, TAX_CODE_2, IUV_2, IUR_2));
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            reader.initToBeNotify();

            assertEquals(1L, reader.read().getRtId());
            assertEquals(2L, reader.read().getRtId());
            assertNull(reader.read());
        }

        @Test
        @DisplayName("should return null immediately when no items")
        void shouldReturnNullImmediatelyWhenNoItems() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            reader.initToBeNotify();

            assertNull(reader.read());
        }
    }

    @Nested
    @DisplayName("convertToLong")
    class ConvertToLongTest {

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported id types")
        void shouldThrowForUnsupportedIdTypes() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[0] = Integer.valueOf(1); // unsupported type
            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertThrows(IllegalArgumentException.class, () -> reader.initToBeNotify());
        }
    }

    /**
     * Regressione: se una cella opzionale del ResultSet e' {@code null} gli helper
     * {@code convertTo*} cadevano sul branch di errore che tenta
     * {@code object.getClass()} sollevando {@link NullPointerException} prima
     * dell'{@link IllegalArgumentException}, facendo abortire {@code beforeStep}.
     * Ora ogni helper ritorna {@code null} se il valore in ingresso e' {@code null}.
     */
    @Nested
    @DisplayName("null-safe converters")
    class NullSafeConvertersTest {

        @Test
        @DisplayName("date null nella riga NON fanno esplodere il reader")
        void nullOptionalDatesDoNotThrow() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[7] = null;   // r.data
            row[9] = null;   // fr.dataOraFlusso
            row[11] = null;  // fr.dataRegolamento
            row[12] = null;  // fr.dataOraPubblicazione
            row[13] = null;  // fr.dataOraAggiornamento

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertDoesNotThrow(reader::initToBeNotify);

            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertEquals(1L, result.getRtId());
            assertNull(result.getData());
            assertNull(result.getDataFlusso());
            assertNull(result.getDataRegolamento());
            assertNull(result.getDataOraPubblicazione());
            assertNull(result.getDataOraAggiornamento());
        }

        @Test
        @DisplayName("importo null nella riga -> RtNotifyContext.importo == null")
        void nullBigDecimalIsPropagated() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[5] = null; // r.importoPagato

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertDoesNotThrow(reader::initToBeNotify);
            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertNull(result.getImporto());
        }

        @Test
        @DisplayName("id null (r.id) viene propagato come null da convertToLong")
        void nullIdIsPropagated() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[0] = null;

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertDoesNotThrow(reader::initToBeNotify);
            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertNull(result.getRtId());
        }

        @Test
        @DisplayName("indice/esito/revisione null NON fanno esplodere il builder (RtNotifyContext usa Integer boxed)")
        void nullIntegerFieldsAreBoxedNotUnboxed() {
            // RtNotifyContext ha campi indice/esito/revisione dichiarati come Integer (nullable)
            // proprio per evitare NPE da auto-unboxing nel builder Lombok quando convertToInteger
            // ritorna null (colonne che nel legacy possono essere NULL, in particolare fr.revisione).
            // Se in futuro qualcuno reintroduce un primitivo int, questo test fallisce.
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[4] = null;   // r.indiceDati
            row[6] = null;   // r.esito
            row[16] = null;  // fr.revisione

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            assertDoesNotThrow(reader::initToBeNotify);
            RtNotifyContext result = reader.read();
            assertNotNull(result);
            assertNull(result.getIndice());
            assertNull(result.getEsito());
            assertNull(result.getRevisione());
        }

        @Test
        @DisplayName("un tipo non supportato continua a sollevare IllegalArgumentException, senza NPE")
        void unsupportedTypeStillThrowsIllegalArgument() {
            RtNotifyReader reader = new RtNotifyReader(rndRepository, FINESTRA_TEMPORALE);

            Object[] row = buildRow(1L, TAX_CODE_1, IUV_1, IUR_1);
            row[7] = "not-a-date"; // tipo non supportato per convertToOffsetDateTime

            List<Object[]> results = new ArrayList<>();
            results.add(row);
            when(rndRepository.findRendicontazioneWithNoPagamentoAfterId(any(LocalDateTime.class)))
                    .thenReturn(results);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> reader.initToBeNotify());
            assertTrue(ex.getMessage().contains("OffsetDateTime"));
        }
    }
}
