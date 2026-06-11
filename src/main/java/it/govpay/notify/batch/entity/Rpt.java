package it.govpay.notify.batch.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity per la tabella {@code rpt} di GovPay.
 * <p>
 * Sono mappati solo i campi necessari alla costruzione del payload
 * {@code it.govpay.ec.client.beans.Ricevuta} (v2 EC API) e al lookup
 * dei parametri della chiamata HTTP.
 */
@Entity
@Table(name = "RPT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rpt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "ccp", nullable = false, length = 35)
    private String ccp;

    @Column(name = "cod_carrello", length = 35)
    private String codCarrello;

    @Column(name = "cod_sessione", length = 255)
    private String codSessione;

    @Column(name = "cod_sessione_portale", length = 255)
    private String codSessionePortale;

    @Column(name = "modello_pagamento", length = 16)
    private String modelloPagamento;

    @Column(name = "data_msg_ricevuta")
    private LocalDateTime dataMsgRicevuta;

    @Column(name = "cod_esito_pagamento")
    private Integer codEsitoPagamento;

    @Column(name = "importo_totale_pagato")
    private BigDecimal importoTotalePagato;

    @Column(name = "xml_rt")
    private byte[] xmlRt;

    @Column(name = "tipo_identificativo_attestante", length = 1)
    private String tipoIdentificativoAttestante;

    @Column(name = "identificativo_attestante", length = 35)
    private String identificativoAttestante;

    @Column(name = "denominazione_attestante", length = 70)
    private String denominazioneAttestante;

    @Column(name = "cod_psp", length = 35)
    private String codPsp;

    @Column(name = "cod_canale", length = 35)
    private String codCanale;

    @Column(name = "id_versamento", nullable = false)
    private Long idVersamento;

    /**
     * Versione SANP della coppia RPT/RT (es. SANP_230, SANP_240, SANP_321_V2,
     * RPTV1_RTV2, RPTV2_RTV1). Determina il {@code RicevutaRt.TipoEnum} nel
     * payload v2 (CT_RICEVUTA_TELEMATICA per SANP_230, CT_RECEIPT altrimenti).
     */
    @Column(name = "versione", nullable = false, length = 35)
    private String versione;
}
