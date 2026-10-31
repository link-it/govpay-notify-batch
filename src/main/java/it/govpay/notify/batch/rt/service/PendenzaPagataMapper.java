package it.govpay.notify.batch.rt.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.IbanAccreditoEntity;
import it.govpay.common.entity.TributoEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IbanAccreditoRepository;
import it.govpay.common.repository.TipoVersamentoRepository;
import it.govpay.common.repository.TributoRepository;
import it.govpay.common.repository.UnitaOperativaRepository;
import it.govpay.ec.client.beans.Documento;
import it.govpay.ec.client.beans.Dominio;
import it.govpay.ec.client.beans.PendenzaPagata;
import it.govpay.ec.client.beans.RiscossioneVocePagata;
import it.govpay.ec.client.beans.Soggetto;
import it.govpay.ec.client.beans.TipoRiferimentoVocePendenza;
import it.govpay.ec.client.beans.TipoRiscossione;
import it.govpay.ec.client.beans.TipoSoggetto;
import it.govpay.ec.client.beans.TipoSogliaVincoloPagamento;
import it.govpay.ec.client.beans.UnitaOperativa;
import it.govpay.ec.client.beans.VincoloPagamento;
import it.govpay.ec.client.beans.VocePendenzaPagata;
import it.govpay.notify.batch.repository.PendenzaNotificaRepository;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.entity.SoggettoDebitore;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.exception.CausaleNonLeggibileException;
import it.govpay.pendenze.service.CausaleVersamentoCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * Costruisce la {@link PendenzaPagata} della ricevuta dal modello dati v3 di
 * govpay-common-pendenze. Riprende {@code PendenzePagateConverter} del monolite, con le
 * regole v3:
 * <ul>
 * <li>soggetto pagatore: il soggetto debitore con {@code ordine = 0} della posizione;</li>
 * <li>documento: la posizione debitoria, tranne quelle create dalla migrazione
 *     ({@code MIGR-<id>}), che prima della 3.11 non esistevano;</li>
 * <li>rata e soglia: da tipologia e giorni dell'opzione di pagamento;</li>
 * <li>causale: quella della pendenza, altrimenti la descrizione della posizione.</li>
 * </ul>
 * I campi senza equivalente v3 (anno di riferimento, cartella, tassonomia, direzione,
 * divisione, dati allegati, proprieta', UUID; contabilita' e metadata delle voci) non si
 * valorizzano.
 */
@Slf4j
@Component
public class PendenzaPagataMapper {

    /** Prefisso dei documenti creati dalla patch 3.11.0 per le pendenze senza documento. */
    static final String PREFISSO_DOCUMENTO_MIGRATO = "MIGR-";

    /** Unita' operativa fittizia che rappresenta l'ente stesso. */
    private static final String UO_ENTE = "EC";

    /** Codifica del tipo bollo "Imposta di bollo" in {@code singoli_versamenti.tipo_bollo}. */
    private static final String TIPO_BOLLO_IMPOSTA = "01";

    private final PendenzaNotificaRepository pendenzaNotificaRepository;
    private final ApplicazioneRepository applicazioneRepository;
    private final DominioRepository dominioRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;
    private final TributoRepository tributoRepository;
    private final IbanAccreditoRepository ibanAccreditoRepository;
    private final ZoneId applicationZoneId;

    public PendenzaPagataMapper(PendenzaNotificaRepository pendenzaNotificaRepository,
                                ApplicazioneRepository applicazioneRepository,
                                DominioRepository dominioRepository,
                                UnitaOperativaRepository unitaOperativaRepository,
                                TipoVersamentoRepository tipoVersamentoRepository,
                                TributoRepository tributoRepository,
                                IbanAccreditoRepository ibanAccreditoRepository,
                                ZoneId applicationZoneId) {
        this.pendenzaNotificaRepository = pendenzaNotificaRepository;
        this.applicazioneRepository = applicazioneRepository;
        this.dominioRepository = dominioRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
        this.tributoRepository = tributoRepository;
        this.ibanAccreditoRepository = ibanAccreditoRepository;
        this.applicationZoneId = applicationZoneId;
    }

    /**
     * @throws PendenzaNonMigrataException se la pendenza non ha opzione di pagamento
     */
    public PendenzaPagata toPendenzaPagata(Pendenza pendenza, List<Pagamento> pagamenti) {
        OpzionePagamento opzione = pendenza.getOpzionePagamento();
        if (opzione == null) {
            throw new PendenzaNonMigrataException(pendenza.getId());
        }
        PosizioneDebitoria posizione = opzione.getPosizioneDebitoria();

        PendenzaPagata rsModel = new PendenzaPagata();
        rsModel.setIdA2A(applicazioneRepository.findById(pendenza.getIdApplicazione())
                .map(a -> a.getCodApplicazione())
                .orElse(null));
        rsModel.setIdPendenza(pendenza.getIdPendenza());
        rsModel.setIdTipoPendenza(tipoVersamentoRepository.findById(pendenza.getIdTipoVersamento())
                .map(t -> t.getCodTipoVersamento())
                .orElse(null));
        rsModel.setDominio(toDominio(pendenza.getIdDominio()));
        rsModel.setUnitaOperativa(toUnitaOperativa(posizione.getIdUnitaOperativa()));
        rsModel.setCausale(toCausale(pendenza, posizione));
        rsModel.setSoggettoPagatore(pendenzaNotificaRepository.findSoggettoPrincipale(posizione.getId())
                .map(PendenzaPagataMapper::toSoggetto)
                .orElse(null));
        rsModel.setImporto(BigDecimal.valueOf(pendenza.getImporto()));
        rsModel.setNumeroAvviso(pendenza.getNumeroAvviso());
        rsModel.setDataValidita(toLocalDate(pendenza.getDataValidita()));
        rsModel.setDataScadenza(toLocalDate(pendenza.getDataScadenzaAvviso()));
        rsModel.setDocumento(toDocumento(pendenza, opzione, posizione));

        for (VocePendenza voce : pendenza.getVoci()) {
            Pagamento pagamento = pagamenti.stream()
                    .filter(p -> p.getIndiceDati() == voce.getIndice())
                    .findFirst()
                    .orElse(null);
            rsModel.addVociItem(toVoce(voce, pagamento));
        }
        return rsModel;
    }

    private String toCausale(Pendenza pendenza, PosizioneDebitoria posizione) {
        if (pendenza.getCausaleVersamento() != null) {
            try {
                return CausaleVersamentoCodec.decode(pendenza.getCausaleVersamento());
            } catch (CausaleNonLeggibileException e) {
                log.warn("Causale non leggibile per la pendenza {}: uso la descrizione della posizione",
                        pendenza.getId());
            }
        }
        return posizione.getDescrizione();
    }

    private Documento toDocumento(Pendenza pendenza, OpzionePagamento opzione, PosizioneDebitoria posizione) {
        if (posizione.getCodDocumento().startsWith(PREFISSO_DOCUMENTO_MIGRATO)) {
            return null;
        }
        Documento documento = new Documento();
        documento.setIdentificativo(posizione.getCodDocumento());
        documento.setDescrizione(posizione.getDescrizione());
        switch (opzione.getTipologia()) {
            case PIANO_RATEALE -> documento.setRata(BigDecimal.valueOf(pendenza.getNumeroRata()));
            case SOLUZIONE_UNICA_ENTRO -> documento.setSoglia(toSoglia(TipoSogliaVincoloPagamento.ENTRO, opzione));
            case SOLUZIONE_UNICA_OLTRE -> documento.setSoglia(toSoglia(TipoSogliaVincoloPagamento.OLTRE, opzione));
            // RIDOTTO e SCONTATO non sono nella TipoSogliaVincoloPagamento di govpay-ec-client
            case SOLUZIONE_UNICA, SOLUZIONE_UNICA_RIDOTTO, SOLUZIONE_UNICA_SCONTATO -> { }
        }
        return documento;
    }

    private static VincoloPagamento toSoglia(TipoSogliaVincoloPagamento tipo, OpzionePagamento opzione) {
        VincoloPagamento soglia = new VincoloPagamento();
        soglia.setTipo(tipo);
        if (opzione.getGiorni() != null) {
            soglia.setGiorni(BigDecimal.valueOf(opzione.getGiorni()));
        }
        return soglia;
    }

    private VocePendenzaPagata toVoce(VocePendenza voce, Pagamento pagamento) {
        VocePendenzaPagata rsModel = new VocePendenzaPagata();
        rsModel.setIdVocePendenza(voce.getIdVocePendenza());
        rsModel.setDescrizione(voce.getDescrizione());
        rsModel.setDescrizioneCausaleRPT(voce.getDescrizioneCausaleRpt());
        if (voce.getIdDominio() != null) {
            rsModel.setDominio(toDominio(voce.getIdDominio()));
        }

        if (voce.getHashDocumento() != null && voce.getTipoBollo() != null && voce.getProvinciaResidenza() != null) {
            rsModel.setHashDocumento(voce.getHashDocumento());
            if (TIPO_BOLLO_IMPOSTA.equals(voce.getTipoBollo())) {
                rsModel.setTipoBollo(TipoRiferimentoVocePendenza.TipoBolloEnum._01);
            }
            rsModel.setProvinciaResidenza(voce.getProvinciaResidenza());
            rsModel.setCodiceTassonomicoPagoPA(codiceTassonomico(voce));
        } else if (voce.getIdTributo() != null) {
            rsModel.setCodEntrata(tributoRepository.findById(voce.getIdTributo())
                    .map(TributoEntity::getTipoTributo)
                    .map(t -> t.getCodTributo())
                    .orElse(null));
        } else {
            rsModel.setCodiceTassonomicoPagoPA(codiceTassonomico(voce));
            rsModel.setIbanAccredito(codIban(voce.getIdIbanAccredito()));
            rsModel.setIbanAppoggio(codIban(voce.getIdIbanAppoggio()));
        }

        if (pagamento != null) {
            rsModel.setRiscossione(toRiscossione(pagamento));
        }
        return rsModel;
    }

    private static String codiceTassonomico(VocePendenza voce) {
        if (voce.getTipoContabilita() != null && voce.getCodiceContabilita() != null) {
            return voce.getTipoContabilita().getCodifica() + "/" + voce.getCodiceContabilita();
        }
        return voce.getCodiceContabilita();
    }

    private String codIban(Long idIban) {
        if (idIban == null) {
            return null;
        }
        return ibanAccreditoRepository.findById(idIban).map(IbanAccreditoEntity::getCodIban).orElse(null);
    }

    private RiscossioneVocePagata toRiscossione(Pagamento pagamento) {
        RiscossioneVocePagata rsModel = new RiscossioneVocePagata();
        rsModel.setIdDominio(pagamento.getCodDominio());
        rsModel.setIuv(pagamento.getIuv());
        rsModel.setIur(pagamento.getIur());
        rsModel.setIndice(BigDecimal.valueOf(pagamento.getIndiceDati()));
        rsModel.setImporto(BigDecimal.valueOf(pagamento.getImportoPagato()));
        rsModel.setData(pagamento.getDataPagamento());
        if (pagamento.getTipo() != null) {
            rsModel.setTipo(TipoRiscossione.fromValue(pagamento.getTipo().name()));
        }
        return rsModel;
    }

    private Dominio toDominio(Long idDominio) {
        return dominioRepository.findById(idDominio)
                .map(PendenzaPagataMapper::toDominio)
                .orElse(null);
    }

    private static Dominio toDominio(DominioEntity entity) {
        Dominio dominio = new Dominio();
        dominio.setIdDominio(entity.getCodDominio());
        dominio.setRagioneSociale(entity.getRagioneSociale());
        return dominio;
    }

    private UnitaOperativa toUnitaOperativa(Long idUnitaOperativa) {
        if (idUnitaOperativa == null) {
            return null;
        }
        return unitaOperativaRepository.findById(idUnitaOperativa)
                .filter(uo -> !UO_ENTE.equals(uo.getCodUo()))
                .map(uo -> {
                    UnitaOperativa rsModel = new UnitaOperativa();
                    rsModel.setIdUnita(uo.getCodUo());
                    rsModel.setRagioneSociale(uo.getUoDenominazione());
                    return rsModel;
                })
                .orElse(null);
    }

    private static Soggetto toSoggetto(SoggettoDebitore soggetto) {
        Soggetto rsModel = new Soggetto();
        if (soggetto.getTipo() != null) {
            rsModel.setTipo(TipoSoggetto.fromValue(soggetto.getTipo().name()));
        }
        rsModel.setIdentificativo(soggetto.getIdentificativo());
        rsModel.setAnagrafica(soggetto.getAnagrafica());
        rsModel.setIndirizzo(soggetto.getIndirizzo());
        rsModel.setCivico(soggetto.getCivico());
        rsModel.setCap(soggetto.getCap());
        rsModel.setLocalita(soggetto.getLocalita());
        rsModel.setProvincia(soggetto.getProvincia());
        rsModel.setNazione(soggetto.getNazione());
        rsModel.setEmail(soggetto.getEmail());
        rsModel.setCellulare(soggetto.getCellulare());
        return rsModel;
    }

    private LocalDate toLocalDate(OffsetDateTime data) {
        return data != null ? data.atZoneSameInstant(applicationZoneId).toLocalDate() : null;
    }
}
