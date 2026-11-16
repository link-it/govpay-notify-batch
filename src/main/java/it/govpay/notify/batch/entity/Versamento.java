package it.govpay.notify.batch.entity;

import it.govpay.common.entity.DominioEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a single payment position
 */
@Entity
@Table(name = "VERSAMENTI")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Versamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dominio", nullable = false)
    private DominioEntity dominio;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_applicazione", nullable = false)
    private Applicazione applicazione;

}
