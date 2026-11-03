package it.govpay.notify.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.pendenze.entity.Pagamento;

/**
 * Letture sulle entity di govpay-common-pendenze per costruire la {@code PendenzaPagata}
 * della ricevuta che la libreria non offre.
 * <p>
 * La pendenza con voci, opzione, posizione e soggetti debitori si legge con
 * {@code PosizioneDebitoriaService#trovaPendenzaCompletaPerId} della libreria; qui restano
 * le riscossioni della RPT, che {@code PagamentoRepository} non cerca per RPT.
 */
@Repository
public interface PendenzaNotificaRepository extends org.springframework.data.repository.Repository<Pagamento, Long> {

    /** Riscossioni della RPT, una per voce pagata. */
    @Query("SELECT pg FROM Pagamento pg WHERE pg.rpt.id = :idRpt ORDER BY pg.indiceDati")
    List<Pagamento> findPagamenti(@Param("idRpt") Long idRpt);
}
