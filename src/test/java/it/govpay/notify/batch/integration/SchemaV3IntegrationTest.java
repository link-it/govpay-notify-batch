package it.govpay.notify.batch.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import it.govpay.common.repository.DominioRepository;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rendicontazione;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.entity.StatoSpedizione;
import it.govpay.notify.batch.entity.TipoNotifica;
import it.govpay.notify.batch.repository.NotificheRepository;
import it.govpay.notify.batch.repository.PendenzaNotificaRepository;
import it.govpay.notify.batch.repository.RendicontazioniRepository;
import it.govpay.notify.batch.rt.service.PendenzaPagataMapper;
import it.govpay.notify.batch.rt.service.RicevutaV2Mapper;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;

/**
 * Compatibilita' del batch con lo schema GovPay 3.11.0, dove le pendenze sono nel
 * modello v3 di govpay-common-pendenze.
 * <p>
 * A differenza degli altri test di integrazione (H2, schema generato dalle entity del
 * batch), qui lo schema e' quello reale del core: {@code ddl-auto=validate} fa fallire
 * l'avvio se un'entity non corrisponde. I dati sono una pendenza v3 con debitore
 * segnaposto su {@code versamenti}, su cui le query dei due job devono funzionare come
 * su una pendenza v2, e da cui si costruisce la ricevuta per l'Ente.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "govpay.batch.job-repository.jdbc.enabled=false"
})
@Sql("/db/postgresql/dati-modello-v3.sql")
@Import({ SchemaV3IntegrationTest.MapperConfig.class, PendenzaPagataMapper.class, RicevutaV2Mapper.class,
        EnteApiClientConfig.class })
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

    /** Anagrafiche di govpay-common e fuso orario, fuori dalla slice JPA. */
    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = DominioRepository.class)
    static class MapperConfig {
        @Bean
        ZoneId applicationZoneId() {
            return ZoneId.of("Europe/Rome");
        }
    }

    private static final long ID_NOTIFICA = 1L;
    private static final long ID_RENDICONTAZIONE = 1L;

    @Autowired
    private NotificheRepository notificheRepository;

    @Autowired
    private RendicontazioniRepository rendicontazioniRepository;

    @Autowired
    private PendenzaNotificaRepository pendenzaNotificaRepository;

    @Autowired
    private RicevutaV2Mapper ricevutaV2Mapper;

    @Autowired
    private EnteApiClientConfig enteApiClientConfig;

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

    @Test
    @DisplayName("la ricevuta di una pendenza v3 ha la sezione pendenza e tutti i campi obbligatori dello schema EC")
    void ricevutaConPendenzaV3() {
        Rpt rpt = entityManager.find(Rpt.class, 1L);
        Pendenza pendenza = pendenzaNotificaRepository.findConVoci(rpt.getIdVersamento()).orElseThrow();
        List<Pagamento> pagamenti = pendenzaNotificaRepository.findPagamenti(rpt.getId());
        assertEquals(1, pendenza.getVoci().size());
        assertEquals(1, pagamenti.size());
        assertEquals("RSSMRA80A01H501U", pendenzaNotificaRepository
                .findSoggettoPrincipale(pendenza.getOpzionePagamento().getPosizioneDebitoria().getId())
                .orElseThrow().getIdentificativo());

        Ricevuta ricevuta = ricevutaV2Mapper.toRicevuta(rpt, pendenza, pagamenti);
        JsonNode json = enteApiClientConfig.createEnteObjectMapper().valueToTree(ricevuta);

        // "stato" e' tra i required di Ricevuta ma non tra le sue proprieta': l'esito e' in "esito".
        for (String campo : List.of("dominio", "iuv", "idRicevuta", "importo", "esito", "istitutoAttestante",
                "modello", "pendenza")) {
            assertTrue(json.has(campo), "Ricevuta senza " + campo);
        }
        JsonNode pendenzaJson = json.get("pendenza");
        assertEquals("APP_V3", pendenzaJson.get("idA2A").asString());
        assertEquals("PND-V3-1", pendenzaJson.get("idPendenza").asString());
        assertEquals("IMU", pendenzaJson.get("idTipoPendenza").asString());
        assertEquals("RSSMRA80A01H501U", pendenzaJson.get("soggettoPagatore").get("identificativo").asString());
        assertEquals("POS-V3-1", pendenzaJson.get("documento").get("identificativo").asString());

        JsonNode voce = pendenzaJson.get("voci").get(0);
        assertEquals("VOCE-V3-1", voce.get("idVocePendenza").asString());
        assertEquals("TRIB-IMU", voce.get("codEntrata").asString());
        JsonNode riscossione = voce.get("riscossione");
        assertNotNull(riscossione, "voce senza riscossione");
        for (String campo : List.of("iuv", "iur", "indice", "tipo", "importo", "data")) {
            assertTrue(riscossione.has(campo), "riscossione senza " + campo);
        }
    }
}
