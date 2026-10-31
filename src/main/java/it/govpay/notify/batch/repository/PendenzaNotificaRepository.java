package it.govpay.notify.batch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.SoggettoDebitore;

/**
 * Letture sulle entity di govpay-common-pendenze per costruire la {@code PendenzaPagata}
 * della ricevuta.
 * <p>
 * {@link #findConVoci} sostituisce {@code PendenzaRepository.findByIdConVoci}, richiesto in
 * link-it/govpay-common-pendenze#13: quando la libreria lo avra', questo metodo si toglie.
 * Il soggetto debitore e' una query a parte perche' voci e soggetti sono entrambi liste e
 * Hibernate non ne carica due nella stessa {@code join fetch}.
 */
@Repository
public interface PendenzaNotificaRepository extends org.springframework.data.repository.Repository<Pendenza, Long> {

    /**
     * Pendenza con voci, opzione di pagamento e posizione debitoria. Opzione e posizione
     * mancano solo per le pendenze scartate dalla migrazione al modello v3.
     */
    @Query("SELECT DISTINCT p FROM Pendenza p " +
           "LEFT JOIN FETCH p.voci " +
           "LEFT JOIN FETCH p.opzionePagamento o " +
           "LEFT JOIN FETCH o.posizioneDebitoria " +
           "WHERE p.id = :id")
    Optional<Pendenza> findConVoci(@Param("id") Long id);

    /** Il soggetto debitore principale della posizione ({@code ordine = 0}). */
    @Query("SELECT s FROM SoggettoDebitore s " +
           "WHERE s.posizioneDebitoria.id = :idPosizione AND s.ordine = 0")
    Optional<SoggettoDebitore> findSoggettoPrincipale(@Param("idPosizione") Long idPosizione);

    /** Riscossioni della RPT, una per voce pagata. */
    @Query("SELECT pg FROM Pagamento pg WHERE pg.rpt.id = :idRpt ORDER BY pg.indiceDati")
    List<Pagamento> findPagamenti(@Param("idRpt") Long idRpt);
}
