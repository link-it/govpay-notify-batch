package it.govpay.notify.batch.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.entity.DominioEntity;
import it.govpay.notify.batch.entity.Fr;
import it.govpay.notify.batch.entity.Rendicontazione;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("integration")
@DisplayName("RendicontazioniRepository Integration Test")
class RendicontazioniRepositoryTest {

    // id_utenza e' obbligatorio e univoco da govpay-common 5a4bfea: ogni
    // applicazione salvata in un test ne riceve uno diverso (JUnit ricrea
    // l'istanza per ogni test, quindi il contatore riparte da 1).
    private long prossimaIdUtenza = 1;

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
        // Lo schema H2 nasce dall'unione delle entity del batch e di govpay-common-pendenze,
        // che mappano entrambe fr e rendicontazioni: queste colonne sono obbligatorie solo per
        // la libreria e le entity del batch non le scrivono. Lo schema reale e' verificato da
        // SchemaV3IntegrationTest.
        for (String colonna : List.of("fr.obsoleto", "fr.stato", "fr.cod_dominio", "rendicontazioni.stato")) {
            String[] parti = colonna.split("\\.");
            entityManager.createNativeQuery("ALTER TABLE " + parti[0] + " ALTER COLUMN " + parti[1] + " SET NULL")
                    .executeUpdate();
        }
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
                .idUtenza(prossimaIdUtenza++)
                .build();
        entityManager.persist(app);
        VocePendenza voce = persistVoce(dominio, app, "");

        Fr fr = Fr.builder()
                .dominio(dominio)
                .codFlusso(COD_FLUSSO)
                .codPsp(COD_PSP)
                .revisione(1L)
                .dataAcquisizione(LocalDateTime.now().minusDays(100)) // older than 90 days
                .build();
        entityManager.persist(fr);

        entityManager.persist(Rendicontazione.builder()
                .fr(fr).idSingoloVersamento(voce.getId()).iuv(IUV).iur(IUR).importoPagato(100.0).build());
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
                .fr(fr).idSingoloVersamento(null).iuv(IUV).iur(IUR).importoPagato(100.0).build());
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
                .idUtenza(prossimaIdUtenza++)
                .build();
        entityManager.persist(app);
        VocePendenza voce = persistVoce(dominio, app, suffix);

        Fr fr = Fr.builder()
                .dominio(dominio)
                .codFlusso(COD_FLUSSO)
                .codPsp(COD_PSP)
                .revisione(1L)
                .dataAcquisizione(LocalDateTime.now())
                .build();
        entityManager.persist(fr);

        Rendicontazione rnd = Rendicontazione.builder()
                .fr(fr)
                .idSingoloVersamento(voce.getId())
                .iuv(iuv)
                .iur(iur)
                .importoPagato(100.0)
                .idPagamento(idPagamento)
                .build();
        entityManager.persist(rnd);
        return rnd;
    }

    /** Pendenza v3 con una voce, scritta con le entity di govpay-common-pendenze. */
    private VocePendenza persistVoce(DominioEntity dominio, ApplicazioneEntity app, String suffix) {
        OffsetDateTime adesso = OffsetDateTime.now();
        Pendenza pendenza = new Pendenza();
        pendenza.setIdDominio(dominio.getId());
        pendenza.setIdApplicazione(app.getId());
        pendenza.setIdPendenza("PND" + suffix);
        pendenza.setIdTipoPendenza(1L);
        pendenza.setIdTipoVersamento(1L);
        pendenza.setNumeroRata(-1);
        pendenza.setImporto(100.0);
        pendenza.setDataCreazione(adesso);
        pendenza.setDataUltimoAggiornamento(adesso);

        VocePendenza voce = new VocePendenza();
        voce.setIdVocePendenza("VOCE" + suffix);
        voce.setImporto(100.0);
        voce.setIndice(1);
        voce.setIdDominio(dominio.getId());
        voce.setStato(StatoVocePendenza.ESEGUITO);
        pendenza.addVocePendenza(voce);

        entityManager.persist(pendenza);
        return voce;
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
