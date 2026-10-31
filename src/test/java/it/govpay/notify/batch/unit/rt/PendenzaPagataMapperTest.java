package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.IbanAccreditoEntity;
import it.govpay.common.entity.TipoTributoEntity;
import it.govpay.common.entity.TipoVersamentoEntity;
import it.govpay.common.entity.TributoEntity;
import it.govpay.common.entity.UnitaOperativaEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IbanAccreditoRepository;
import it.govpay.common.repository.TipoVersamentoRepository;
import it.govpay.common.repository.TributoRepository;
import it.govpay.common.repository.UnitaOperativaRepository;
import it.govpay.ec.client.beans.PendenzaPagata;
import it.govpay.ec.client.beans.TipoRiferimentoVocePendenza;
import it.govpay.ec.client.beans.TipoRiscossione;
import it.govpay.ec.client.beans.TipoSoggetto;
import it.govpay.ec.client.beans.TipoSogliaVincoloPagamento;
import it.govpay.ec.client.beans.VocePendenzaPagata;
import it.govpay.notify.batch.repository.PendenzaNotificaRepository;
import it.govpay.notify.batch.rt.service.PendenzaNonMigrataException;
import it.govpay.notify.batch.rt.service.PendenzaPagataMapper;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.entity.SoggettoDebitore;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.TipoPagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.pendenze.service.CausaleVersamentoCodec;

@DisplayName("PendenzaPagataMapper")
class PendenzaPagataMapperTest {

    private static final long ID_DOMINIO = 1L;
    private static final long ID_DOMINIO_BENEFICIARIO = 2L;
    private static final long ID_POSIZIONE = 30L;

    private PendenzaNotificaRepository pendenzaNotificaRepository;
    private UnitaOperativaRepository unitaOperativaRepository;
    private TributoRepository tributoRepository;
    private IbanAccreditoRepository ibanAccreditoRepository;
    private PendenzaPagataMapper mapper;

    @BeforeEach
    void setUp() {
        pendenzaNotificaRepository = mock(PendenzaNotificaRepository.class);
        ApplicazioneRepository applicazioneRepository = mock(ApplicazioneRepository.class);
        DominioRepository dominioRepository = mock(DominioRepository.class);
        unitaOperativaRepository = mock(UnitaOperativaRepository.class);
        TipoVersamentoRepository tipoVersamentoRepository = mock(TipoVersamentoRepository.class);
        tributoRepository = mock(TributoRepository.class);
        ibanAccreditoRepository = mock(IbanAccreditoRepository.class);

        when(applicazioneRepository.findById(10L))
                .thenReturn(Optional.of(ApplicazioneEntity.builder().codApplicazione("APP_V3").build()));
        when(dominioRepository.findById(ID_DOMINIO)).thenReturn(Optional.of(
                DominioEntity.builder().codDominio("12345678901").ragioneSociale("Ente").build()));
        when(dominioRepository.findById(ID_DOMINIO_BENEFICIARIO)).thenReturn(Optional.of(
                DominioEntity.builder().codDominio("98765432109").ragioneSociale("Beneficiario").build()));
        TipoVersamentoEntity tipo = new TipoVersamentoEntity();
        tipo.setCodTipoVersamento("IMU");
        when(tipoVersamentoRepository.findById(20L)).thenReturn(Optional.of(tipo));
        when(pendenzaNotificaRepository.findSoggettoPrincipale(ID_POSIZIONE))
                .thenReturn(Optional.of(soggetto("RSSMRA80A01H501U")));

        mapper = new PendenzaPagataMapper(pendenzaNotificaRepository, applicazioneRepository, dominioRepository,
                unitaOperativaRepository, tipoVersamentoRepository, tributoRepository, ibanAccreditoRepository,
                ZoneId.of("Europe/Rome"));
    }

    // -------------------------------------------------------------------------
    // Dati di prova
    // -------------------------------------------------------------------------

    private static SoggettoDebitore soggetto(String identificativo) {
        SoggettoDebitore s = new SoggettoDebitore();
        s.setOrdine(0);
        s.setTipo(it.govpay.pendenze.model.TipoSoggetto.F);
        s.setIdentificativo(identificativo);
        s.setAnagrafica("Mario Rossi");
        s.setEmail("mario.rossi@example.org");
        return s;
    }

    private static Pendenza pendenza(TipologiaOpzionePagamento tipologia, String codDocumento) {
        PosizioneDebitoria posizione = new PosizioneDebitoria();
        posizione.setId(ID_POSIZIONE);
        posizione.setCodDocumento(codDocumento);
        posizione.setDescrizione("Posizione di test");

        OpzionePagamento opzione = new OpzionePagamento();
        opzione.setTipologia(tipologia);
        opzione.setPosizioneDebitoria(posizione);

        Pendenza pendenza = new Pendenza();
        pendenza.setId(100L);
        pendenza.setIdApplicazione(10L);
        pendenza.setIdTipoVersamento(20L);
        pendenza.setIdDominio(ID_DOMINIO);
        pendenza.setIdPendenza("PND-1");
        pendenza.setNumeroRata(-1);
        pendenza.setImporto(100.0);
        pendenza.setNumeroAvviso("301000000000000001");
        pendenza.setOpzionePagamento(opzione);
        pendenza.addVocePendenza(voce(1, "VOCE-1"));
        return pendenza;
    }

    private static VocePendenza voce(int indice, String id) {
        VocePendenza voce = new VocePendenza();
        voce.setIdVocePendenza(id);
        voce.setIndice(indice);
        voce.setImporto(100.0);
        voce.setDescrizione("Voce " + indice);
        voce.setIdTributo(50L);
        return voce;
    }

    private static Pagamento pagamento(int indiceDati) {
        Pagamento p = new Pagamento();
        p.setCodDominio("12345678901");
        p.setIuv("01000000000000001");
        p.setIur("IUR-1");
        p.setIndiceDati(indiceDati);
        p.setImportoPagato(100.0);
        p.setDataPagamento(OffsetDateTime.of(2026, 10, 30, 10, 0, 0, 0, ZoneOffset.UTC));
        p.setTipo(TipoPagamento.ENTRATA);
        return p;
    }

    private void stubTributo(String codTributo) {
        TipoTributoEntity tipoTributo = new TipoTributoEntity();
        tipoTributo.setCodTributo(codTributo);
        TributoEntity tributo = new TributoEntity();
        tributo.setTipoTributo(tipoTributo);
        when(tributoRepository.findById(50L)).thenReturn(Optional.of(tributo));
    }

    // -------------------------------------------------------------------------
    // Casi
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SOLUZIONE_UNICA: anagrafiche, soggetto con ordine 0, documento senza rata ne' soglia, voce con riscossione")
    void soluzioneUnica() {
        stubTributo("TRIB-1");

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1"),
                List.of(pagamento(1)));

        assertEquals("APP_V3", p.getIdA2A());
        assertEquals("PND-1", p.getIdPendenza());
        assertEquals("IMU", p.getIdTipoPendenza());
        assertEquals("12345678901", p.getDominio().getIdDominio());
        assertEquals("Ente", p.getDominio().getRagioneSociale());
        assertEquals(new BigDecimal("100.0"), p.getImporto());
        assertEquals("301000000000000001", p.getNumeroAvviso());
        assertEquals("Posizione di test", p.getCausale());

        assertEquals(TipoSoggetto.F, p.getSoggettoPagatore().getTipo());
        assertEquals("RSSMRA80A01H501U", p.getSoggettoPagatore().getIdentificativo());
        assertEquals("mario.rossi@example.org", p.getSoggettoPagatore().getEmail());

        assertEquals("POS-1", p.getDocumento().getIdentificativo());
        assertEquals("Posizione di test", p.getDocumento().getDescrizione());
        assertNull(p.getDocumento().getRata());
        assertNull(p.getDocumento().getSoglia());

        assertEquals(1, p.getVoci().size());
        VocePendenzaPagata voce = p.getVoci().get(0);
        assertEquals("VOCE-1", voce.getIdVocePendenza());
        assertEquals("TRIB-1", voce.getCodEntrata());
        assertNotNull(voce.getRiscossione());
        assertEquals("IUR-1", voce.getRiscossione().getIur());
        assertEquals(new BigDecimal("1"), voce.getRiscossione().getIndice());
        assertEquals(TipoRiscossione.ENTRATA, voce.getRiscossione().getTipo());
    }

    @Test
    @DisplayName("PIANO_RATEALE: il documento porta il numero di rata")
    void pianoRateale() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.PIANO_RATEALE, "POS-1");
        pendenza.setNumeroRata(2);

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of());

        assertEquals(new BigDecimal("2"), p.getDocumento().getRata());
        assertNull(p.getDocumento().getSoglia());
    }

    @Test
    @DisplayName("SOLUZIONE_UNICA_ENTRO: soglia ENTRO con i giorni dell'opzione")
    void soluzioneUnicaEntro() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, "POS-1");
        pendenza.getOpzionePagamento().setGiorni(30);

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of());

        assertEquals(TipoSogliaVincoloPagamento.ENTRO, p.getDocumento().getSoglia().getTipo());
        assertEquals(new BigDecimal("30"), p.getDocumento().getSoglia().getGiorni());
        assertNull(p.getDocumento().getRata());
    }

    @Test
    @DisplayName("opzione migrata da RIDOTTO: documento senza soglia, perche' govpay-ec-client non ha RIDOTTO")
    void soluzioneUnicaRidotto() {
        PendenzaPagata p = mapper.toPendenzaPagata(
                pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO, "POS-1"), List.of());

        assertEquals("POS-1", p.getDocumento().getIdentificativo());
        assertNull(p.getDocumento().getSoglia());
    }

    @Test
    @DisplayName("posizione creata dalla migrazione (MIGR-<id>): nessun documento, come prima della 3.11")
    void posizioneMigrata() {
        PendenzaPagata p = mapper.toPendenzaPagata(
                pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "MIGR-100"), List.of());

        assertNull(p.getDocumento());
        assertEquals("RSSMRA80A01H501U", p.getSoggettoPagatore().getIdentificativo());
    }

    @Test
    @DisplayName("causale della pendenza, se presente, al posto della descrizione della posizione")
    void causaleDellaPendenza() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        pendenza.setCausaleVersamento(CausaleVersamentoCodec.encode("Rata IMU 2026"));

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of());

        assertEquals("Rata IMU 2026", p.getCausale());
    }

    @Test
    @DisplayName("unita' operativa della posizione, tranne quella fittizia dell'ente (EC)")
    void unitaOperativa() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        pendenza.getOpzionePagamento().getPosizioneDebitoria().setIdUnitaOperativa(70L);
        UnitaOperativaEntity uo = new UnitaOperativaEntity();
        uo.setCodUo("UO-1");
        uo.setUoDenominazione("Ufficio tributi");
        when(unitaOperativaRepository.findById(70L)).thenReturn(Optional.of(uo));

        assertEquals("UO-1", mapper.toPendenzaPagata(pendenza, List.of()).getUnitaOperativa().getIdUnita());

        uo.setCodUo("EC");
        assertNull(mapper.toPendenzaPagata(pendenza, List.of()).getUnitaOperativa());
    }

    @Test
    @DisplayName("voce multi-beneficiario: dominio della voce, IBAN e codice tassonomico, riscossione per indice")
    void voceMultiBeneficiario() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        VocePendenza seconda = voce(2, "VOCE-2");
        seconda.setIdTributo(null);
        seconda.setIdDominio(ID_DOMINIO_BENEFICIARIO);
        seconda.setIdIbanAccredito(60L);
        seconda.setCodiceContabilita("9/0101100IM/");
        pendenza.addVocePendenza(seconda);
        IbanAccreditoEntity iban = new IbanAccreditoEntity();
        iban.setCodIban("IT60X0542811101000000123456");
        when(ibanAccreditoRepository.findById(60L)).thenReturn(Optional.of(iban));
        when(tributoRepository.findById(any())).thenReturn(Optional.empty());

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of(pagamento(1), pagamento(2)));

        VocePendenzaPagata voce = p.getVoci().get(1);
        assertEquals("98765432109", voce.getDominio().getIdDominio());
        assertEquals("IT60X0542811101000000123456", voce.getIbanAccredito());
        assertNull(voce.getIbanAppoggio());
        assertEquals("9/0101100IM/", voce.getCodiceTassonomicoPagoPA());
        assertEquals(new BigDecimal("2"), voce.getRiscossione().getIndice());
    }

    @Test
    @DisplayName("voce bollo: hash, tipo bollo 01 e provincia; voce senza riscossione se manca il pagamento")
    void voceBollo() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        VocePendenza bollo = pendenza.getVoci().get(0);
        bollo.setIdTributo(null);
        bollo.setHashDocumento("aGFzaA==");
        bollo.setTipoBollo("01");
        bollo.setProvinciaResidenza("RM");

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of());

        VocePendenzaPagata voce = p.getVoci().get(0);
        assertEquals("aGFzaA==", voce.getHashDocumento());
        assertEquals(TipoRiferimentoVocePendenza.TipoBolloEnum._01, voce.getTipoBollo());
        assertEquals("RM", voce.getProvinciaResidenza());
        assertNull(voce.getCodEntrata());
        assertNull(voce.getRiscossione());
    }

    @Test
    @DisplayName("date di validita' e scadenza nel fuso dell'applicazione")
    void date() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        // 23:30 UTC del 30 e' gia' il 31 a Roma
        pendenza.setDataScadenzaAvviso(OffsetDateTime.of(2026, 10, 30, 23, 30, 0, 0, ZoneOffset.UTC));

        PendenzaPagata p = mapper.toPendenzaPagata(pendenza, List.of());

        assertEquals(LocalDate.of(2026, 10, 31), p.getDataScadenza());
        assertNull(p.getDataValidita());
    }

    @Test
    @DisplayName("pendenza scartata dalla migrazione (senza opzione) -> PendenzaNonMigrataException")
    void pendenzaScartata() {
        Pendenza pendenza = pendenza(TipologiaOpzionePagamento.SOLUZIONE_UNICA, "POS-1");
        pendenza.setOpzionePagamento(null);
        List<Pagamento> nessuno = List.of();

        assertThrows(PendenzaNonMigrataException.class, () -> mapper.toPendenzaPagata(pendenza, nessuno));
    }
}
