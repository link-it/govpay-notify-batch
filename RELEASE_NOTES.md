# Release Notes

## v1.0.3 — 2026-06-30

Release di manutenzione: tre fix per lo startup del container e per la
ricompilabilita' del progetto dal sorgente. Nessun cambio funzionale.

### Fix

- **Bean wiring di `govpay-common` mancante**: aggiunta classe
  `it.govpay.notify.batch.config.BatchInfraConfig` che dichiara come
  `@Bean` `JobExecutionHelper` e `JobConcurrencyService`. La libreria
  comune (`1.1.2`) espone questi tipi solo via factory statiche in
  `BatchCommonAutoConfiguration`, non come bean auto-registrati. Senza
  la config esplicita lo startup falliva con
  `No qualifying bean of type 'it.govpay.common.batch.runner.JobExecutionHelper' available`
  perché `AbstractScheduledJobRunner` e `AbstractCronJobRunner` richiedono
  il bean. Allineato a `BatchInfraConfig` di `govpay-rt-batch`.

- **`LOADER_PATH` non propagato all'entrypoint Docker**: aggiunto
  `export LOADER_PATH="${GOVPAY_DS_JDBC_LIBS}"` in
  `docker/commons/entrypoint.sh` prima dell'`exec` del jar. Senza,
  `PropertiesLauncher` non includeva la directory dei driver JDBC nel
  classpath e l'avvio falliva con
  `Cannot load driver class: org.postgresql.Driver`
  anche quando il volume `jdbc-drivers` era montato correttamente.
  Aggiunto anche log di conferma del path attivo.

- **`EnteApiService.notifyRendicontazione` non compilava dal sorgente**:
  la chiamata `NuovaRendicontazione.EsitoEnum.fromValue(BigDecimal)` era
  rotta perché la signature dell'enum di `govpay-ec-client:1.0.0` è
  `fromValue(String)`. Introdotto helper privato `mapEsito(int)` che
  mappa esplicitamente i codici esito pagoPA (0 → `ESEGUITO`,
  3 → `REVOCATO`, 4 → `ESEGUITO_STANDIN`,
  8 → `ESEGUITO_STANDIN_SENZA_RPT`, 9 → `ESEGUITO_SENZA_RPT`) all'enum
  strong-typed. Bug pre-esistente nella `1.0.2` (citato nelle
  RELEASE_NOTES `1.0.1`).

### Dipendenze principali

Nessun aggiornamento rispetto a 1.0.2.

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `io.netty:netty-bom` (override) | `4.1.133.Final` |

### Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.3.jar` | Fat JAR eseguibile (driver JDBC esclusi) |
| `release-reports-1.0.3.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

---

## v1.0.2 — 2026-05-12

Release di manutenzione: cleanup della configurazione di logging di base e
soppressione motivata di una vulnerabilita' Netty non sfruttabile.

### Sicurezza

- **CVE [GHSA-rwm7-x88c-3g2p](https://osv.dev/GHSA-rwm7-x88c-3g2p)** (CVSS 7.5)
  su `io.netty:netty-transport-native-epoll`: classificata come falso positivo
  e soppressa in `src/main/resources/osv/falsePositives/osv-scanner.toml`
  (con `ignoreUntil = 2026-11-12`). La vulnerabilita' richiede un socket
  server con trasporto epoll e `ALLOW_HALF_CLOSURE` abilitato; il batch
  usa Reactor Netty esclusivamente lato client (WebClient verso pagoPA),
  quindi il path di sfruttamento non e' raggiungibile. L'unica patch upstream
  e' in Netty 4.2.13.Final, incompatibile con la linea Reactor Netty 1.2.x
  adottata da Spring Boot 3.5: da rivalutare al passaggio a Reactor Netty
  1.3.x / Netty 4.2.x.

### Pipeline

- Il job `osv-scan` del workflow `maven.yml` riceve ora `--config` per
  caricare il file di soppressioni dedicato a OSV-Scanner.

### Configurazione

- Rimosse le direttive `logging.level.*` da
  `src/main/resources/application.properties`:
  - `logging.level.root=INFO`
  - `logging.level.it.govpay.notify.batch=DEBUG`
  - `logging.level.org.springframework.batch=DEBUG`
  - `logging.level.org.springframework.web.client=DEBUG`
  - `logging.level.com.fasterxml.jackson=DEBUG`
- I livelli di logging non sono piu' fissati nella configurazione di base
  e vanno demandati al runtime (variabili d'ambiente, profili Spring
  dedicati, configurazione esterna). Il blocco logging gia' presente in
  `src/test/resources/application-integration.properties` resta invariato.
- Eliminata in particolare la direttiva `com.fasterxml.jackson=DEBUG`,
  che produceva log molto verbosi ed era un residuo di debug puntuale.

### Note per chi aggiorna

- Se in produzione si faceva affidamento sui livelli precedenti, vanno
  ora dichiarati esplicitamente nella configurazione di deployment
  (es. `application-<profilo>.properties`, variabile `LOGGING_LEVEL_*` o
  `--logging.level.*` da riga di comando). Vedi sezione *Logging* del
  `README.md`.

### Dipendenze principali

Nessun aggiornamento rispetto a 1.0.1.

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `io.netty:netty-bom` (override) | `4.1.133.Final` |

### Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.2.jar` | Fat JAR eseguibile (driver JDBC esclusi) |
| `release-reports-1.0.2.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

---

## v1.0.1 — 2026-05-06

Release di manutenzione: fix di sicurezza, robustezza della pipeline ed
estensione della copertura dei test.

### Sicurezza

- **CVE [GHSA-v8h7-rr48-vmmv](https://osv.dev/GHSA-v8h7-rr48-vmmv)** (CVSS 5.3)
  su `io.netty:netty-codec-http`: importato `io.netty:netty-bom:4.1.133.Final`
  in `dependencyManagement` per allineare tutti i moduli Netty alla versione
  che corregge la vulnerabilita'.

### Pipeline

- Lo step di zip SQL diventa tollerante: se `src/main/resources/sql/` non
  esiste, lo step viene saltato senza far fallire la build.

### Test

- Aggiunti unit test per:
  - **utils**: `LocalDateFlexibleDeserializer`, `OffsetDateTimeDeserializer`,
    `OffsetDateTimeSerializer`.
  - **config**: `BatchJobConfiguration`, `EnteApiClientConfig`, `TimezoneConfig`,
    `CronJobRunner`, `ScheduledJobRunner`.
  - **controller**: `BatchController`.
  - **service**: `EnteApiService` (path preflight e `clearCache`).
- Estesa la copertura di `EventoRtMapper` sulla copia degli header di risposta
  da `ResponseEntity` e `HttpStatusCodeException`.

### Note tecniche

- I path HTTP di `EnteApiService.notifyRendicontazione` non sono coperti dai
  test in 1.0.1: `NuovaRendicontazione$EsitoEnum` in `govpay-ec-client:1.0.0`
  ha tutti i valori dichiarati come `new BigDecimal("null")` e fallisce in
  `<clinit>` con `NumberFormatException` al primo accesso. Verra' affrontato
  con il prossimo rilascio di `govpay-ec-client`.

### Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `io.netty:netty-bom` (override) | `4.1.133.Final` |

### Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.1.jar` | Fat JAR eseguibile (driver JDBC esclusi) |
| `release-reports-1.0.1.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

---

## v1.0.0 — 2026-05-06

Prima release di **govpay-notify-batch**, batch di spedizione delle ricevute
e dei pagamenti salvo buon fine verso pagoPA.

### Funzionalita'

- Applicazione Spring Boot / Spring Batch (Java 21).
- Persistenza JPA con supporto multi-database tramite driver JDBC forniti
  esternamente (PostgreSQL, MySQL, MS SQL Server, Oracle, H2).
- Integrazione con il Gestore Diagnostico Eventi (GDE) per il tracciamento
  asincrono degli eventi.
- Endpoint Actuator per health check e metriche.

### Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |

### Distribuzione

- **Fat JAR** Spring Boot con layout `ZIP` (PropertiesLauncher): i driver JDBC
  non sono inclusi e vanno forniti tramite `loader.path` esterno.
- **Immagine Docker** `linkitaly/govpay-notify-batch:1.0.0` pubblicata su Docker Hub.
- Script SQL di inizializzazione disponibili come asset di release (`sql.zip`).

### Pipeline CI/CD

La pipeline di rilascio include:

- Build e test con cache Maven e timezone `Europe/Rome`.
- Code coverage **JaCoCo** e analisi statica **SonarCloud**.
- Scansione vulnerabilita' **OWASP Dependency-Check** (cache NVD aggiornata
  da workflow notturno dedicato).
- Scansione vulnerabilita' **Google OSV Scanner** (output SARIF).
- Generazione **SBOM CycloneDX** (json + xml) tramite `cyclonedx-maven-plugin`.
- Analisi licenze delle dipendenze transitive con eccezioni gestite via
  `license-exceptions.json`.

### Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.0.jar` | Fat JAR eseguibile (driver JDBC esclusi) |
| `sql.zip` | Script SQL di inizializzazione |
| `release-reports-1.0.0.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |
