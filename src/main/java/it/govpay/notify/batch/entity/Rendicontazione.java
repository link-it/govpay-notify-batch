package it.govpay.notify.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a single payment
 */
@Entity
@Table(name = "RENDICONTAZIONI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rendicontazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fr", nullable = false)
    private Fr fr;

    @Column(name = "id_pagamento")
    private Long idPagamento;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_singolo_versamento")
    private SingoloVersamento singoloVersamento;

    @Column(name = "data")
    private LocalDateTime data;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "indice_dati")
    private Integer indiceDati;

    @Column(name = "importo_pagato", nullable = false)
    private Double importoPagato;

    @Column(name = "esito")
    private Integer esito;

    @Column(name = "notifica_inviata", nullable = false)
    @Builder.Default
    private Boolean notificaInviata = false;

    @Column(name = "esegui_recupero_rt", nullable = false)
    @Builder.Default
    private Boolean eseguiRecuperoRt = false;

}
