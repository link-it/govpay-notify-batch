package it.govpay.notify.batch.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entity per la tabella {@code notifiche} di GovPay.
 * <p>
 * Replica la coda di spedizione del monolite: {@link StatoSpedizione#DA_SPEDIRE}
 * + {@code data_prossima_spedizione < now} -> il record va inviato all'Ente.
 * Allineata a {@code it.govpay.bd.model.Notifica} del monolite per i campi
 * letti/aggiornati dal batch.
 */
@Entity
@Table(name = "NOTIFICHE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notifica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_applicazione", nullable = false)
    private Applicazione applicazione;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rpt")
    private Rpt rpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_esito", nullable = false, length = 16)
    private TipoNotifica tipoEsito;

    @Enumerated(EnumType.STRING)
    @Column(name = "stato", nullable = false, length = 16)
    private StatoSpedizione stato;

    @Column(name = "descrizione_stato", length = 255)
    private String descrizioneStato;

    @Column(name = "tentativi_spedizione")
    private Long tentativiSpedizione;

    @Column(name = "data_creazione", nullable = false)
    private LocalDateTime dataCreazione;

    @Column(name = "data_aggiornamento_stato", nullable = false)
    private LocalDateTime dataAggiornamentoStato;

    @Column(name = "data_prossima_spedizione", nullable = false)
    private LocalDateTime dataProssimaSpedizione;
}
