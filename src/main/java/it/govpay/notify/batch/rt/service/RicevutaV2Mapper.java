package it.govpay.notify.batch.rt.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import it.govpay.ec.client.beans.Dominio;
import it.govpay.ec.client.beans.EsitoRpp;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.ec.client.beans.RicevutaIstitutoAttestante;
import it.govpay.ec.client.beans.RicevutaRt;
import it.govpay.notify.batch.entity.Rpt;

/**
 * Costruisce il payload {@link Ricevuta} v2 dell'EC API a partire dalla
 * entity {@link Rpt}. Replica la semantica di
 * {@code it.govpay.core.ec.v2.converter.RicevuteConverter} del monolite,
 * limitatamente ai campi necessari alla spedizione (il batch non ha JAXB,
 * quindi non valorizza il sotto-payload {@code rpt}/{@code rt.json} —
 * solo l'XML grezzo dell'RT).
 */
@Component
public class RicevutaV2Mapper {

    private final ZoneId applicationZoneId;

    public RicevutaV2Mapper(ZoneId applicationZoneId) {
        this.applicationZoneId = applicationZoneId;
    }

    public Ricevuta toRicevuta(Rpt rpt) {
        Ricevuta ricevuta = new Ricevuta();

        Dominio dominio = new Dominio();
        dominio.setIdDominio(rpt.getCodDominio());
        ricevuta.setDominio(dominio);

        ricevuta.setIuv(rpt.getIuv());
        ricevuta.setIdRicevuta(rpt.getCcp());
        ricevuta.setImporto(rpt.getImportoTotalePagato());
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
