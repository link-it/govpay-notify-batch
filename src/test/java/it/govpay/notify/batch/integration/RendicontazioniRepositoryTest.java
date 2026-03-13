package it.govpay.notify.batch.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.entity.DominioEntity;
import it.govpay.notify.batch.entity.Applicazione;
import it.govpay.notify.batch.entity.Fr;
import it.govpay.notify.batch.entity.Rendicontazione;
import it.govpay.notify.batch.entity.SingoloVersamento;
import it.govpay.notify.batch.entity.Versamento;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("integration")
@DisplayName("RendicontazioniRepository Integration Test")
class RendicontazioniRepositoryTest {

    @Autowired
    private RendicontazioniRepository rendicontazioniRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456789";
    private static final String COD_APPLICAZIONE = "APP001";
    private static final String FIRMA_RICEVUTA = "0";
    private static final String COD_FLUSSO = "FLUSSO001";
    private static final String COD_PSP = "PSP001";
    private static final LocalDateTime DATA_LIMITE = LocalDateTime.now().minusDays(90);

    @BeforeEach
    void setUp() {
        rendicontazioniRepository.deleteAll();
    }

    @Test
    @DisplayName("should find rendicontazione when all conditions are met")
    void shouldFindRendicontazioneWhenAllConditionsMet() {
        createTestData(IUV, IUR, null);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertEquals(1, results.size());
        Object[] row = results.get(0);
        assertEquals(TAX_CODE, row[1]); // codDominio
        assertEquals(IUV, row[2]);      // iuv
        assertEquals(IUR, row[3]);      // iur
        assertEquals(COD_APPLICAZIONE, row[17]); // codApplicazione
    }

    @Test
    @DisplayName("should not find rendicontazione with idPagamento set")
    void shouldNotFindRendicontazioneWithPagamento() {
        createTestData(IUV, IUR, 12345L);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should not find rendicontazione with notificaInviata true")
    void shouldNotFindRendicontazioneWithNotificaInviata() {
        Rendicontazione rnd = createTestData(IUV, IUR, null);
        rnd.setNotificaInviata(true);
        entityManager.persist(rnd);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should not find rendicontazione with eseguiRecuperoRt true")
    void shouldNotFindRendicontazioneWithEseguiRecuperoRt() {
        Rendicontazione rnd = createTestData(IUV, IUR, null);
        rnd.setEseguiRecuperoRt(true);
        entityManager.persist(rnd);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should not find rendicontazione with dataAcquisizione older than limit")
    void shouldNotFindRendicontazioneOlderThanDataLimite() {
        DominioEntity dominio = buildDominio(TAX_CODE);
        entityManager.persist(dominio);

        ApplicazioneEntity app = ApplicazioneEntity.builder()
                .codApplicazione(COD_APPLICAZIONE)
                .autoIuv(false)
                .firmaRicevuta(FIRMA_RICEVUTA)
                .trusted(false)
                .build();
        entityManager.persist(app);
        Applicazione applicazione = entityManager.find(Applicazione.class, app.getId());

        Versamento versamento = Versamento.builder().dominio(dominio).applicazione(applicazione).build();
        entityManager.persist(versamento);

        Fr fr = Fr.builder()
                .dominio(dominio)
                .codFlusso(COD_FLUSSO)
                .codPsp(COD_PSP)
                .revisione(1L)
                .dataAcquisizione(LocalDateTime.now().minusDays(100)) // older than 90 days
                .build();
        entityManager.persist(fr);

        SingoloVersamento sv = SingoloVersamento.builder().versamento(versamento).build();
        entityManager.persist(sv);

        entityManager.persist(Rendicontazione.builder()
                .fr(fr).singoloVersamento(sv).iuv(IUV).iur(IUR).importoPagato(100.0).build());
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should not find rendicontazione without singoloVersamento")
    void shouldNotFindRendicontazioneWithoutSingoloVersamento() {
        DominioEntity dominio = buildDominio(TAX_CODE);
        entityManager.persist(dominio);

        Fr fr = Fr.builder()
                .dominio(dominio).codFlusso(COD_FLUSSO).codPsp(COD_PSP)
                .revisione(1L).dataAcquisizione(LocalDateTime.now()).build();
        entityManager.persist(fr);

        entityManager.persist(Rendicontazione.builder()
                .fr(fr).singoloVersamento(null).iuv(IUV).iur(IUR).importoPagato(100.0).build());
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("should return multiple results ordered by id")
    void shouldReturnResultsOrderedById() {
        createTestData("_1", IUV + "_1", IUR + "_1", null);
        createTestData("_2", IUV + "_2", IUR + "_2", null);
        createTestData("_3", IUV + "_3", IUR + "_3", null);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(DATA_LIMITE);

        assertEquals(3, results.size());
        Long prevId = 0L;
        for (Object[] row : results) {
            Long currentId = ((Number) row[0]).longValue();
            assertTrue(currentId > prevId, "Results should be ordered by id ascending");
            prevId = currentId;
        }
    }

    @Test
    @DisplayName("should set notificaInviata to true")
    void shouldRegisterNotificaRt() {
        Rendicontazione rnd = createTestData(IUV, IUR, null);
        entityManager.flush();
        entityManager.clear();

        rendicontazioniRepository.registerNotificaRt(rnd.getId());
        entityManager.flush();
        entityManager.clear();

        Rendicontazione updated = entityManager.find(Rendicontazione.class, rnd.getId());
        assertTrue(updated.getNotificaInviata());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Rendicontazione createTestData(String iuv, String iur, Long idPagamento) {
        return createTestData("", iuv, iur, idPagamento);
    }

    private Rendicontazione createTestData(String suffix, String iuv, String iur, Long idPagamento) {
        DominioEntity dominio = buildDominio(TAX_CODE + suffix);
        entityManager.persist(dominio);

        ApplicazioneEntity app = ApplicazioneEntity.builder()
                .codApplicazione(COD_APPLICAZIONE + suffix)
                .autoIuv(false)
                .firmaRicevuta(FIRMA_RICEVUTA)
                .trusted(false)
                .build();
        entityManager.persist(app);
        Applicazione applicazione = entityManager.find(Applicazione.class, app.getId());

        Versamento versamento = Versamento.builder().dominio(dominio).applicazione(applicazione).build();
        entityManager.persist(versamento);

        Fr fr = Fr.builder()
                .dominio(dominio)
                .codFlusso(COD_FLUSSO)
                .codPsp(COD_PSP)
                .revisione(1L)
                .dataAcquisizione(LocalDateTime.now())
                .build();
        entityManager.persist(fr);

        SingoloVersamento sv = SingoloVersamento.builder().versamento(versamento).build();
        entityManager.persist(sv);

        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .singoloVersamento(sv)
                .iuv(iuv)
                .iur(iur)
                .importoPagato(100.0)
                .idPagamento(idPagamento)
                .build();
        entityManager.persist(rnd);
        return rnd;
    }

    private DominioEntity buildDominio(String codDominio) {
        return DominioEntity.builder()
                .codDominio(codDominio)
                .abilitato(true)
                .ragioneSociale("Test")
                .auxDigit(0)
                .intermediato(true)
                .scaricaFr(false)
                .build();
    }
}
