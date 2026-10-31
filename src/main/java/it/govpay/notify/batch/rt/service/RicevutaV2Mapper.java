package it.govpay.notify.batch.rt.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.ec.client.beans.Dominio;
import it.govpay.ec.client.beans.EsitoRpp;
import it.govpay.ec.client.beans.ModelloPagamento;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.ec.client.beans.RicevutaIstitutoAttestante;
import it.govpay.ec.client.beans.RicevutaRt;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;

/**
 * Costruisce il payload {@link Ricevuta} v2 dell'EC API a partire dalla
 * entity {@link Rpt}. Replica la semantica di
 * {@code it.govpay.core.ec.v2.converter.RicevuteConverter} del monolite,
 * limitatamente ai campi necessari alla spedizione (il batch non ha JAXB,
 * quindi non valorizza il sotto-payload {@code rpt}/{@code rt.json} —
 * solo l'XML grezzo dell'RT). La sezione {@code pendenza} e' di {@link PendenzaPagataMapper}.
 */
@Component
public class RicevutaV2Mapper {

    private final ZoneId applicationZoneId;
    private final PendenzaPagataMapper pendenzaPagataMapper;

    public RicevutaV2Mapper(ZoneId applicationZoneId, PendenzaPagataMapper pendenzaPagataMapper) {
        this.applicationZoneId = applicationZoneId;
        this.pendenzaPagataMapper = pendenzaPagataMapper;
    }

    /**
     * @throws IllegalStateException se la pendenza della RPT non e' stata trovata
     * @throws PendenzaNonMigrataException se la pendenza non e' nel modello v3
     */
    public Ricevuta toRicevuta(Rpt rpt, Pendenza pendenza, List<Pagamento> pagamenti) {
        if (pendenza == null) {
            throw new IllegalStateException("Pendenza non trovata per la RPT " + rpt.getId()
                    + " (id_versamento " + rpt.getIdVersamento() + ")");
        }
        Ricevuta ricevuta = new Ricevuta();

        Dominio dominio = new Dominio();
        dominio.setIdDominio(rpt.getCodDominio());
        ricevuta.setDominio(dominio);

        ricevuta.setIuv(rpt.getIuv());
        ricevuta.setIdRicevuta(rpt.getCcp());
        ricevuta.setImporto(rpt.getImportoTotalePagato() != null
                ? BigDecimal.valueOf(rpt.getImportoTotalePagato())
                : null);
        ricevuta.setEsito(toEsitoRpp(rpt.getCodEsitoPagamento()));

        OffsetDateTime dataRicevuta = rpt.getDataMsgRicevuta() != null
                ? rpt.getDataMsgRicevuta().atZone(applicationZoneId).toOffsetDateTime()
                : null;
        ricevuta.setData(dataRicevuta);
        ricevuta.setDataPagamento(dataRicevuta);

        if (rpt.getIdentificativoAttestante() != null) {
            RicevutaIstitutoAttestante istituto = new RicevutaIstitutoAttestante();
            istituto.setIdPSP(rpt.getIdentificativoAttestante());
            istituto.setDenominazione(rpt.getDenominazioneAttestante());
            istituto.setIdCanale(rpt.getCodCanale());
            ricevuta.setIstitutoAttestante(istituto);
        }

        if (rpt.getXmlRt() != null) {
            RicevutaRt ricevutaRt = new RicevutaRt();
            ricevutaRt.setXml(rpt.getXmlRt());
            ricevutaRt.setTipo(tipoRtFromVersione(rpt.getVersione()));
            ricevuta.setRt(ricevutaRt);
        }

        ricevuta.setPendenza(pendenzaPagataMapper.toPendenzaPagata(pendenza, pagamenti));
        ricevuta.setModello(ModelloPagamento.PAGAMENTO_AD_INIZIATIVA_PSP);

        return ricevuta;
    }

    /**
     * Mappa il codice esito numerico (colonna {@code cod_esito_pagamento})
     * sull'enum {@link EsitoRpp}. Codici da DDL govpay:
     * 0=Eseguito, 1=Non eseguito, 2=Parzialmente eseguito,
     * 3=Decorrenza, 4=Decorrenza parziale.
     */
    private EsitoRpp toEsitoRpp(Integer codEsito) {
        if (codEsito == null) {
            return null;
        }
        return switch (codEsito) {
            case 0 -> EsitoRpp.ESEGUITO;
            case 1 -> EsitoRpp.NON_ESEGUITO;
            case 2 -> EsitoRpp.ESEGUITO_PARZIALE;
            case 3 -> EsitoRpp.DECORRENZA;
            case 4 -> EsitoRpp.DECORRENZA_PARZIALE;
            default -> throw new IllegalArgumentException("Codice esito pagamento non riconosciuto: " + codEsito);
        };
    }

    /**
     * Sceglie il {@link RicevutaRt.TipoEnum} in funzione della versione SANP
     * della RPT/RT (cfr. {@code RicevuteConverter} del monolite):
     * SANP_230 -> CT_RICEVUTA_TELEMATICA; le altre (SANP_240, SANP_321_V2,
     * RPTV1_RTV2, RPTV2_RTV1) -> CT_RECEIPT.
     */
    private RicevutaRt.TipoEnum tipoRtFromVersione(String versione) {
        if ("SANP_230".equals(versione)) {
            return RicevutaRt.TipoEnum.CT_RICEVUTA_TELEMATICA;
        }
        return RicevutaRt.TipoEnum.CT_RECEIPT;
    }
}
