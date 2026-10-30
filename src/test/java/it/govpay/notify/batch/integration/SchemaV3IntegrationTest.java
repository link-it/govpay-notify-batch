package it.govpay.notify.batch.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rendicontazione;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.entity.StatoSpedizione;
import it.govpay.notify.batch.entity.TipoNotifica;
import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Compatibilita' del batch con lo schema GovPay 3.11.0, dove le pendenze sono nel
 * modello v3 di govpay-common-pendenze.
 * <p>
 * A differenza degli altri test di integrazione (H2, schema generato dalle entity del
 * batch), qui lo schema e' quello reale del core: {@code ddl-auto=validate} fa fallire
 * l'avvio se un'entity non corrisponde. I dati sono una pendenza v3 con debitore
 * segnaposto su {@code versamenti}, su cui le query dei due job devono funzionare come
 * su una pendenza v2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "govpay.batch.job-repository.jdbc.enabled=false"
})
@Sql("/db/postgresql/dati-modello-v3.sql")
@DisplayName("Schema GovPay 3.11.0 (modello v3) su PostgreSQL")
class SchemaV3IntegrationTest {

    // gen_random_uuid() della patch 3.11.0 richiede PostgreSQL 13 o successivo.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/postgresql/gov_pay-3.11.0.sql"),
                    "/docker-entrypoint-initdb.d/01_gov_pay.sql")
            // Le entity batch di govpay-common mappano le tabelle di Spring Batch.
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("org/springframework/batch/core/schema-postgresql.sql"),
                    "/docker-entrypoint-initdb.d/02_spring_batch.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final long ID_NOTIFICA = 1L;
    private static final long ID_RENDICONTAZIONE = 1L;

    @Autowired
    private NotificheRepository notificheRepository;

    @Autowired
    private RendicontazioniRepository rendicontazioniRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("la notifica RICEVUTA su una pendenza v3 viene letta, con la sua RPT, e marcata SPEDITO")
    void notificaRicevutaSuPendenzaV3() {
        List<Notifica> notifiche = notificheRepository.findNotificheDaSpedire(
                TipoNotifica.RICEVUTA, StatoSpedizione.DA_SPEDIRE, LocalDateTime.now(), PageRequest.of(0, 50));

        assertEquals(1, notifiche.size());
        Notifica notifica = notifiche.get(0);
        assertEquals("APP_V3", notifica.getApplicazione().getCodApplicazione());
        Rpt rpt = notifica.getRpt();
        assertEquals("12345678901", rpt.getCodDominio());
        assertEquals("01000000000000001", rpt.getIuv());
        assertEquals("RCP-V3-1", rpt.getCcp());
        assertEquals("SANP_240", rpt.getVersione());
        assertEquals(1L, rpt.getIdVersamento());

        assertEquals(1, notificheRepository.updateSpedito(ID_NOTIFICA, LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(StatoSpedizione.SPEDITO, entityManager.find(Notifica.class, ID_NOTIFICA).getStato());
    }

    @Test
    @DisplayName("la rendicontazione di una pendenza v3 viene trovata con l'applicazione e marcata notificata")
    void rendicontazioneSuPendenzaV3() {
        List<Object[]> righe = rendicontazioniRepository.findRendicontazioneWithNoPagamentoAfterId(
                LocalDateTime.now().minusDays(90));

        assertEquals(1, righe.size());
        Object[] riga = righe.get(0);
        assertEquals("12345678901", riga[1]); // codDominio
        assertEquals("01000000000000001", riga[2]); // iuv
        assertEquals("FLUSSO-V3-1", riga[8]); // codFlusso
        assertEquals("APP_V3", riga[17]); // codApplicazione

        rendicontazioniRepository.registerNotificaRt(ID_RENDICONTAZIONE);
        entityManager.flush();
        entityManager.clear();

        assertTrue(entityManager.find(Rendicontazione.class, ID_RENDICONTAZIONE).getNotificaInviata());
    }
}
