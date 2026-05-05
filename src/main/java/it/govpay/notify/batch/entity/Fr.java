package it.govpay.notify.batch.entity;

import java.time.LocalDateTime;

import it.govpay.common.entity.DominioEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a FDR (Flusso di Rendicontazione)
 */
@Entity
@Table(name = "FR")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dominio", nullable = false)
    private DominioEntity dominio;

    @Column(name = "cod_flusso", nullable = false, length = 35)
    private String codFlusso;

    @Column(name = "iur", length = 35)
    private String iur;

    @Column(name = "data_ora_flusso")
    private LocalDateTime dataOraFlusso;

    @Column(name = "data_regolamento")
    private LocalDateTime dataRegolamento;

    @Column(name = "data_acquisizione")
    private LocalDateTime dataAcquisizione;

    @Column(name = "data_ora_pubblicazione")
    private LocalDateTime dataOraPubblicazione;

    @Column(name = "data_ora_aggiornamento")
    private LocalDateTime dataOraAggiornamento;

    @Column(name = "cod_psp", nullable = false, length = 35)
    private String codPsp;

    @Column(name = "cod_bic_riversamento", length = 35)
    private String codBicRiversamento;

    @Column(name = "revisione", nullable = false)
    private Long revisione;
}
