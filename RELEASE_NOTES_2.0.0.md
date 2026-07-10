# GovPay NOTIFY Batch — Release Notes v2.0.0

Data: 2026-07-10
Branch: `main`
Linea: 2.x (Spring Boot 4.x + `govpay-common` 2.0.x)

Prima release ufficiale della linea **2.x**. Consolida la migrazione
dello stack, aggiunge il servizio di spedizione delle notifiche delle
ricevute di pagamento (RT) e chiude tutti i fix di startup emersi
durante la stabilizzazione.

## Novita' principali

### Migrazione dello stack

Migrata l'intera catena a Spring Boot 4.x / Spring Framework 7.x /
Spring Batch 6.x / Hibernate ORM 7.x / Jackson 3.x (nuovo groupId
`tools.jackson`).

Riorganizzazione dei package Spring Batch 6 nei sorgenti:

- `org.springframework.batch.core.Job/Step/JobExecution/listener` →
  sotto-package `.job` / `.step` / `.listener`
- Item API → `org.springframework.batch.infrastructure.item.*`
- Eccezioni di launch → `org.springframework.batch.core.launch.*`
- `JobParametersInvalidException` rinominato in `InvalidJobParametersException`
- `RunIdIncrementer` → `.job.parameters`
- `@EntityScan` → `org.springframework.boot.persistence.autoconfigure`

Adattamenti applicativi:

- `BatchController` inietta `JobRepository` in luogo di `JobExplorer`
  (rimosso in Spring Batch 6).
- `BatchExecutionRecapListener`: usa `getJobInstanceId()` al posto del
  rimosso `JobExecution.getJobId()`.
- `GdeService`: passato a `tools.jackson.databind.ObjectMapper`
  (Jackson 3), coerente con `AbstractGdeService` di `govpay-common` 2.0.
- `EnteApiService`: mappatura esplicita del codice esito numerico verso
  `NuovaRendicontazione.EsitoEnum` (in `govpay-ec-client` 2.x `fromValue`
  accetta il nome stringa dell'enum, non piu' il codice).

### Porting del servizio di spedizione delle RT

Nuovo job batch indipendente **`rtSendJob`** che spedisce le notifiche
delle ricevute di pagamento (RT) all'API di integrazione dell'Ente:

- Reader paginato sulla tabella `notifiche`
  (`tipo_esito = RICEVUTA`, `stato = DA_SPEDIRE`,
  `data_prossima_spedizione < now`).
- Processor che spedisce via `govpay-ec-client` v2 EC API:
  `PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}`.
- Writer in **transazione per item** con tre transizioni di stato:
  - `SUCCESS` → `updateSpedito`
  - `ERROR` → `updateDaSpedire` con backoff quadratico
    (`tentativi^2 * 60s` capped a 24h)
  - `ABORT` → `updateAnnullata` con sentinel "mai" (`9999-02-01`)
- Runner `@Scheduled` (profilo `default`) con cron Spring
  (default `0 * * * * *`, ogni minuto al secondo 0, in linea col monolite).
- Endpoint `/api/batch/run` lancia entrambi i job (`rtNotifyJob` +
  `rtSendJob` in best-effort).

Nuove entity locali `Notifica`, `Rpt` con enum `StatoSpedizione` /
`TipoNotifica`. `RicevutaV2Mapper` popola il payload `Ricevuta` v2
con i campi disponibili in DB (dominio, iuv, idRicevuta, importo,
esito, data/dataPagamento, istitutoAttestante, `rt` con xml grezzo +
tipo `CT_RICEVUTA_TELEMATICA` / `CT_RECEIPT` in base alla colonna
`versione` della RPT).

Supportata solo la v2 dell'EC API: connettori configurati su versioni
diverse vengono trattati come `ABORT` (notifica annullata). Vedi
`docs/issues/govpay-common-versione-connettore.md`.

### Kill switch spedizione RT

Property `govpay.batch.rt-send.enabled` (default `false`) e' un kill
switch effettivo: quando disabilitata, il runner schedulato non viene
istanziato (`@ConditionalOnProperty`) e il `BatchController` salta anche
il lancio best-effort di `rtSendJob` dall'endpoint `/api/batch/run`.

### Fix startup container

- **`BatchInfraConfig`** (nuova @Configuration): dichiara come `@Bean`
  `JobExecutionHelper` e `JobConcurrencyService` di `govpay-common`.
  La libreria comune espone questi tipi solo via factory statiche in
  `BatchCommonAutoConfiguration`, non come bean auto-registrati. Senza
  la config esplicita lo startup falliva con
  `No qualifying bean of type 'JobExecutionHelper' available`.
- **`docker/commons/entrypoint.sh`**: aggiunto
  `export LOADER_PATH="${GOVPAY_DS_JDBC_LIBS}"` prima dell'`exec` del
  jar. Senza, `PropertiesLauncher` non caricava i driver JDBC dal
  volume `jdbc-drivers` e l'avvio falliva con `Cannot load driver
  class`. Aggiunto log di conferma del path attivo.
- **Rimossa proprieta' `govpay.url`**: iniettata via `@Value` senza
  default ma mai letta, faceva fallire l'avvio con
  `Could not resolve placeholder 'govpay.url'` quando non impostata.
  Rimosso il campo e l'import da `GdeService`.

### Security

- Rimossi gli override e le soppressioni divenuti ridondanti dopo il
  bump di `govpay-bom`:
  - Eliminato l'override `netty-bom 4.1.133.Final` (era forzato per
    CVE `GHSA-v8h7-rr48-vmmv`): il BOM 2.x risolve Netty a 4.2.x,
    posteriore alla fix 4.2.13.Final.
  - Eliminata la soppressione OSV `GHSA-rwm7-x88c-3g2p` (Netty epoll
    DoS): la patch upstream e' presente in 4.2.13.Final.
- Cache OWASP/NVD della pipeline con la versione del plugin nella
  chiave (`runner.os-owasp-v{version}-{date}`), allineata a
  `govpay-common`. Un bump del plugin invalida automaticamente la cache.

## Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | **`2.0.1`** |
| `org.gov4j.govpay:govpay-common` | **`2.0.0`** |
| `org.gov4j.govpay:govpay-ec-client` | **`2.0.1`** |
| Spring Boot | `4.0.x` |
| Spring Framework | `7.0.x` |
| Spring Batch | `6.0.x` |
| Hibernate ORM | `7.2.x` |
| Jackson | `3.x` (`tools.jackson`) affiancato da `2.x` (`com.fasterxml.jackson`, usato dal client EC generato) |

## Compatibilita' e migrazione

Chi era su `1.0.x`:

- Il jar richiede JVM 21+.
- Nessun cambio nelle env-var di configurazione dell'entrypoint Docker
  (`GOVPAY_DB_*`, `GOVPAY_NOTIFY_*`, ecc.).
- La nuova property `govpay.batch.rt-send.enabled` esce con default
  `false`: attivare a `true` solo dopo aver validato la configurazione
  dei connettori EC v2 sull'ambiente.

## Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-2.0.0.jar` | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`) |
| `sql.zip` | Script SQL di inizializzazione (se aggiornati) |
| `release-reports-2.0.0.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

## Riferimenti

- ChangeLog dettagliato: voci dal `2026-06-05` al `2026-07-10` nel file
  [`ChangeLog`](ChangeLog).
- Issue collegate:
  - `docs/issues/govpay-common-versione-connettore.md`
  - #6 (migrazione Spring Boot 4.x / Spring Framework 7.x)
  - #4 (porting spedizione ricevute)
