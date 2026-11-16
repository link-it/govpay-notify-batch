package it.govpay.notify.batch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.govpay.notify.batch.entity.Rendicontazione;

@Repository
public interface RendicontazioniRepository extends JpaRepository<Rendicontazione, Long> {
	@Query("SELECT r.id, d.codDominio, r.iuv, r.iur, r.indiceDati, r.importoPagato, " +
	              "r.esito, r.data, fr.codFlusso, fr.dataOraFlusso, " +
	              "fr.iur, fr.dataRegolamento, fr.dataOraPubblicazione, " +
	              "fr.dataOraAggiornamento, fr.codPsp, " +
	              "fr.codBicRiversamento, fr.revisione, a.codApplicazione " +
	       "FROM Rendicontazione r " +
	              "JOIN r.fr fr " +
	              "JOIN fr.dominio d " +
	              "JOIN r.singoloVersamento sv " +
	              "JOIN sv.versamento v " +
	              "JOIN v.applicazione a " +
	       "WHERE fr.dataAcquisizione > :dataLimite " +
	              "AND r.singoloVersamento IS NOT NULL " +
	              "AND r.idPagamento IS NULL " +
	              "AND r.eseguiRecuperoRt = false " +
	              "AND r.notificaInviata = false")
    List<Object[]> findRendicontazioneWithNoPagamentoAfterId( @Param("dataLimite") LocalDateTime dataLimite );

    @Modifying
    @Query("UPDATE Rendicontazione r SET r.notificaInviata = true WHERE r.id = :id")
    void registerNotificaRt(@Param("id") Long id);
}
