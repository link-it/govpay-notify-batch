# Release Notes

## v1.0.5 — 2026-07-01

Release di manutenzione: rimossa una `@Value` senza default che faceva
fail-fast dell'avvio quando la relativa property non era impostata in
ambiente. Nessun cambio funzionale, dipendenze invariate rispetto a
`1.0.3`.

> **Perché 1.0.5 e non 1.0.4?** Il tag `1.0.4` era stato posizionato su
> un commit del branch `main` (`9aeba17`) privo della `BatchInfraConfig`
> introdotta nella `1.0.3`, quindi l'immagine `1.0.4` non riusciva ad
> avviarsi (`No qualifying bean of type 'JobExecutionHelper' available`).
> Il tag è stato lasciato inalterato per non riposizionare un tag
> pubblico; l'immagine `1.0.4` è **da non utilizzare**. Il fix esce come
> `1.0.5` sul commit corretto del branch `1.x.x`.

### Fix

- **Placeholder `govpay.url` non risolvibile**: il campo `govpayUrl` di
  `GdeService` era iniettato via `@Value("${govpay.url}")` senza
  default, ma non veniva mai letto da nessun metodo. Dead code che
  faceva pero' fallire l'avvio con
  `Could not resolve placeholder 'govpay.url'` non appena la property
  non era impostata (come emerso nel deploy dell'immagine `1.0.3` in
  ambiente locale). Rimosso il campo e il relativo import da
  `GdeService`. Backport dal commit `d93d2bf` di `main`.

### Dipendenze principali

Invariate rispetto a `1.0.3`.

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

### Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.5.jar` | Fat JAR eseguibile (driver JDBC esclusi) |
| `release-reports-1.0.5.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

---

## v1.0.4 — 2026-07-01 · **NON UTILIZZARE**

Rilascio saltato: il tag `1.0.4` è stato posizionato su un commit del
branch `main` privo della `BatchInfraConfig` introdotta nella `1.0.3`.
L'immagine `linkitaly/govpay-notify-batch:1.0.4` non si avvia. Utilizzare
`1.0.5`. Il tag non è stato rimosso per non riposizionare un tag già
pubblicato.

---

## v1.0.3 — 2026-06-30

Release di manutenzione: due fix per lo startup del container + bump
delle dipendenze transitive per chiudere le CVE segnalate da OSV.

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

### Sicurezza

Bump di tre famiglie di dipendenze transitive in risposta alle CVE
segnalate da OSV scan (25 CVE coperte in totale, una soppressa in attesa
di publish upstream):

- **`com.fasterxml.jackson.core:jackson-databind`** `2.21.1` → `2.21.4`
  (importato `jackson-bom` in `dependencyManagement`: la property
  `jackson-bom.version` viene sovrascritta da `govpay-bom 1.1.3`).
  Risolte: GHSA-5hh8-q8hv-fr38, GHSA-9fxm-vc8v-hj55, GHSA-hgj6-7826-r7m5,
  GHSA-j3rv-43j4-c7qm, GHSA-rcqc-6cw3-h962, GHSA-rmj7-2vxq-3g9f.
- **`io.netty:*`** `4.1.133.Final` → `4.1.135.Final` (override
  `netty-bom`, sostituisce il precedente override per
  `GHSA-v8h7-rr48-vmmv`). Risolte: GHSA-hvcg-qmg6-jm4c,
  GHSA-563q-j3cm-6jxm, GHSA-5x3r-wrvg-rp6q, GHSA-c2gf-v879-257j,
  GHSA-3qp7-7mw8-wx86, GHSA-c653-97m9-rcg9, GHSA-x4gw-5cx5-pgmh,
  GHSA-5pvg-856g-cp85, GHSA-676x-f7gg-47vc, GHSA-xmv7-r254-6q78,
  GHSA-w573-9ffj-6ff9.
- **`org.apache.tomcat.embed:tomcat-embed-core`** `10.1.54` → `10.1.55`
  (property `tomcat.version`). Risolte: GHSA-5m62-pw8w-7w9f,
  GHSA-5mp6-jrq3-r938, GHSA-9m89-8frq-c98c, GHSA-fv25-8xcx-gqjc,
  GHSA-gx5v-xp9w-j4cg, GHSA-h6fc-48rj-7qqh, GHSA-r29c-68gh-xp6x.

Soppressione temporanea:

- **GHSA-5jmj-h7xm-6q6v** (CVSS 5.3): il fix indicato da OSV è in
  `jackson-databind 2.21.5`, non ancora pubblicata su Maven Central
  (l'ultima patch della linea `2.21.x` è `2.21.4`). Soppressa in
  `src/main/resources/osv/falsePositives/osv-scanner.toml` con
  `ignoreUntil = 2026-10-15`. Da rivalutare quando `2.21.5` sarà
  disponibile (oppure considerando il salto alla linea `2.22.x`).

### Limitazioni note (invariate da 1.0.1)

- `NuovaRendicontazione.EsitoEnum` di `govpay-ec-client:1.0.0` ha tutti
  i valori dichiarati come `new BigDecimal("null")` (bug del codegen) e
  fallisce in `<clinit>` con `NumberFormatException` al primo accesso.
  Di conseguenza il path HTTP di `EnteApiService.notifyRendicontazione`
  non e' eseguibile a runtime ne' coperto dai test. Verra' affrontato con
  il rilascio di una versione corretta di `govpay-ec-client`.

### Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

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
