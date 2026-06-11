package it.govpay.notify.batch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.StatoSpedizione;
import it.govpay.notify.batch.entity.TipoNotifica;

/**
 * Repository per la tabella {@code notifiche}. Replica la semantica di
 * {@code it.govpay.bd.pagamento.NotificheBD} del monolite GovPay.
 */
@Repository
public interface NotificheRepository extends JpaRepository<Notifica, Long> {

    /**
     * Ricerca le notifiche pronte alla spedizione: tipo specifico,
     * stato {@link StatoSpedizione#DA_SPEDIRE} e
     * {@code data_prossima_spedizione < dataLimite}. L'ordinamento per id
     * garantisce stabilita' tra esecuzioni.
     *
     * @param tipoEsito tipo notifica da selezionare (RICEVUTA per il job RT)
     * @param dataLimite valore con cui confrontare la data prossima spedizione (tipicamente {@code now})
     * @param pageable paginazione applicata al reader del batch
     */
    @Query("SELECT n FROM Notifica n " +
           "WHERE n.tipoEsito = :tipoEsito " +
           "  AND n.stato = :stato " +
           "  AND n.dataProssimaSpedizione < :dataLimite " +
           "ORDER BY n.id ASC")
    List<Notifica> findNotificheDaSpedire(@Param("tipoEsito") TipoNotifica tipoEsito,
                                          @Param("stato") StatoSpedizione stato,
                                          @Param("dataLimite") LocalDateTime dataLimite,
                                          Pageable pageable);

    /**
     * Allinea {@code NotificheBD.updateSpedito(id)}: la notifica e' stata
     * accettata dall'Ente.
     */
    @Modifying
    @Query("UPDATE Notifica n SET " +
           "n.stato = it.govpay.notify.batch.entity.StatoSpedizione.SPEDITO, " +
           "n.descrizioneStato = NULL, " +
           "n.dataAggiornamentoStato = :now " +
           "WHERE n.id = :id")
    int updateSpedito(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Allinea {@code NotificheBD.updateDaSpedire}: errore non bloccante.
     * Non sovrascrive lo stato (potrebbe essere stato chiuso da un altro nodo),
     * aggiorna solo descrizione/tentativi/data prossima spedizione.
     */
    @Modifying
    @Query("UPDATE Notifica n SET " +
           "n.descrizioneStato = :descrizione, " +
           "n.tentativiSpedizione = :tentativi, " +
           "n.dataProssimaSpedizione = :prossima, " +
           "n.dataAggiornamentoStato = :now " +
           "WHERE n.id = :id")
    int updateDaSpedire(@Param("id") Long id,
                        @Param("descrizione") String descrizione,
                        @Param("tentativi") Long tentativi,
                        @Param("prossima") LocalDateTime prossima,
                        @Param("now") LocalDateTime now);

    /**
     * Allinea {@code NotificheBD.updateAnnullata}: configurazione assente
     * o errore terminale, la notifica non verra' piu' ripresa.
     */
    @Modifying
    @Query("UPDATE Notifica n SET " +
           "n.stato = it.govpay.notify.batch.entity.StatoSpedizione.ANNULLATA, " +
           "n.descrizioneStato = :descrizione, " +
           "n.tentativiSpedizione = :tentativi, " +
           "n.dataProssimaSpedizione = :prossima, " +
           "n.dataAggiornamentoStato = :now " +
           "WHERE n.id = :id")
    int updateAnnullata(@Param("id") Long id,
                        @Param("descrizione") String descrizione,
                        @Param("tentativi") Long tentativi,
                        @Param("prossima") LocalDateTime prossima,
                        @Param("now") LocalDateTime now);
}
