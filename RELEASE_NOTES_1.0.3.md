# GovPay NOTIFY Batch — Release Notes v1.0.3

Data: 2026-06-30
Branch: `1.x.x` (HEAD: `98465e1`)
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Release di manutenzione: due fix per lo startup del container + bump
delle dipendenze transitive per chiudere le CVE segnalate da OSV scan.

## Fix

### Bean wiring di `govpay-common` mancante

Aggiunta classe `it.govpay.notify.batch.config.BatchInfraConfig` che
dichiara come `@Bean` `JobExecutionHelper` e `JobConcurrencyService`.

La libreria comune (`1.1.2`) espone questi tipi solo via factory statiche
in `BatchCommonAutoConfiguration`, non come bean auto-registrati. Senza
la config esplicita lo startup falliva con:

```
No qualifying bean of type 'it.govpay.common.batch.runner.JobExecutionHelper' available
```

perche' `AbstractScheduledJobRunner` e `AbstractCronJobRunner` richiedono
il bean nel costruttore. Allineato a `BatchInfraConfig` di
`govpay-rt-batch`.

### `LOADER_PATH` non propagato all'entrypoint Docker

Aggiunto `export LOADER_PATH="${GOVPAY_DS_JDBC_LIBS}"` in
`docker/commons/entrypoint.sh` prima dell'`exec` del jar.

Il fat JAR e' repackaged con layout `ZIP` e `PropertiesLauncher`, e i
driver JDBC (`postgresql`, `mysql`, `oracle`, `sqlserver`) sono in scope
`provided`: vanno aggiunti al classpath via `loader.path`. Senza questa
`export` PropertiesLauncher non vedeva i driver e l'avvio falliva con:

```
Cannot load driver class: org.postgresql.Driver
```

anche quando il volume `jdbc-drivers` era montato correttamente.
Aggiunto anche log di conferma del path attivo.

## Sicurezza

Bump di tre famiglie di dipendenze transitive in risposta alle CVE
segnalate da OSV scan (25 CVE coperte in totale, una soppressa in attesa
di publish upstream):

- **`com.fasterxml.jackson.core:jackson-databind`** `2.21.1` → `2.21.4`
  (importato `jackson-bom` in `dependencyManagement`: la property
  `jackson-bom.version` viene sovrascritta da `govpay-bom 1.1.3`).
  Risolte: GHSA-5hh8-q8hv-fr38, GHSA-9fxm-vc8v-hj55, GHSA-hgj6-7826-r7m5,
  GHSA-j3rv-43j4-c7qm, GHSA-rcqc-6cw3-h962, GHSA-rmj7-2vxq-3g9f.
- **`io.netty:*`** `4.1.133.Final` → `4.1.135.Final` (override
  `netty-bom`, sostituisce il precedente override per `GHSA-v8h7-rr48-vmmv`).
  Risolte: GHSA-hvcg-qmg6-jm4c, GHSA-563q-j3cm-6jxm, GHSA-5x3r-wrvg-rp6q,
  GHSA-c2gf-v879-257j, GHSA-3qp7-7mw8-wx86, GHSA-c653-97m9-rcg9,
  GHSA-x4gw-5cx5-pgmh, GHSA-5pvg-856g-cp85, GHSA-676x-f7gg-47vc,
  GHSA-xmv7-r254-6q78, GHSA-w573-9ffj-6ff9.
- **`org.apache.tomcat.embed:tomcat-embed-core`** `10.1.54` → `10.1.55`
  (property `tomcat.version`). Risolte: GHSA-5m62-pw8w-7w9f,
  GHSA-5mp6-jrq3-r938, GHSA-9m89-8frq-c98c, GHSA-fv25-8xcx-gqjc,
  GHSA-gx5v-xp9w-j4cg, GHSA-h6fc-48rj-7qqh, GHSA-r29c-68gh-xp6x.

Soppressione temporanea:

- **GHSA-5jmj-h7xm-6q6v** (CVSS 5.3): il fix indicato da OSV è in
  `jackson-databind 2.21.5`, non ancora pubblicata su Maven Central
  (l'ultima patch della linea `2.21.x` è `2.21.4`). Soppressa in
  `src/main/resources/osv/falsePositives/osv-scanner.toml` con
  `ignoreUntil = 2026-10-15`.

## Limitazioni note (invariate da 1.0.1)

`NuovaRendicontazione.EsitoEnum` in `govpay-ec-client:1.0.0` ha le
costanti generate come `NUMBER_null`, `NUMBER_null2`, ... con valore
`new BigDecimal("null")` (bug del codegen). Il `<clinit>` della classe
fallisce con `NumberFormatException` al primo accesso, quindi il path
HTTP di `EnteApiService.notifyRendicontazione` resta non eseguibile a
runtime e non coperto dai test (situazione gia' presente nella `1.0.2`).
Verra' affrontato con il rilascio di una versione corretta di
`govpay-ec-client`.

## Compatibilita'

Nessun cambio di firma di env-var o di property. Chi sta su `1.0.2` puo'
aggiornare a `1.0.3` cambiando solo il tag immagine; il deploy via
docker-compose **non** richiede piu' il workaround
`LOADER_PATH: /opt/jdbc-drivers` nel servizio (la variabile e' impostata
dall'entrypoint).

## Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

## Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.3.jar` | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`) |
| `sql.zip` | Script SQL di inizializzazione (se aggiornati) |
| `release-reports-1.0.3.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

## Note operative

- Il prossimo step e' la creazione del tag `1.0.3` sul branch `1.x.x`,
  che fa partire la pipeline `release` + `docker` (GitHub release con
  asset + push dell'immagine `linkitaly/govpay-notify-batch:1.0.3` su
  Docker Hub).
- La linea 2.x (Spring Boot 4 + `govpay-common` 2.0 + porting servizio
  spedizione ricevute) prosegue su `main`.

## Riferimenti

- ChangeLog dettagliato: voci datate 2026-06-30 nel file
  [`ChangeLog`](ChangeLog).
- Commit della patch: `cfd45c7` (fix) + `98465e1` (version bump).
- Issue collegate: vedi PR su GitHub.
