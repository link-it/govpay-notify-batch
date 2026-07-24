# GovPay NOTIFY Batch — Release Notes v1.0.8

Data: 2026-07-15
Branch: `1.x.x`
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Hotfix critico sopra la `1.0.7`.

In produzione, con la `1.0.7` deployata, lo step `rtNotifyStep`
continuava a fallire ogni volta che un record puntava a un connettore
non trovato o non abilitato — **nonostante** il catch "configurazione
non idonea" introdotto in `1.0.7`.

Il sintomo dai log:

```
i.g.c.client.service.ConnettoreService : Connettore non trovato o non abilitato: UNIURB-ESSE3_INTEGRAZIONE
i.g.notify.batch.service.EnteApiService : Notifica saltata per rtId 31689968 ... configurazione non idonea: Connettore non trovato o non abilitato: UNIURB-ESSE3_INTEGRAZIONE
o.s.batch.core.step.AbstractStep : Encountered an error executing step rtNotifyStep in job rtNotifyJob
org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

Il log del "salto" appariva regolarmente (il catch funzionava), ma lo
step falliva comunque al commit del chunk.

## Root cause

`ConnettoreService` in `govpay-common:1.1.2` ha `@Transactional(readOnly = true)`
a **livello di classe** (linea 50 del sorgente). Quando i suoi metodi
sollevano `IllegalArgumentException` — tutti i tre casi:

- `getConnettore`: "Connettore non trovato o non abilitato: ..."
- `getConnettore`: "Connettore non abilitato: ..."
- `getConnettoreAsMap`: "Connettore non trovato: ..."

il `TransactionInterceptor` di Spring, che intercetta l'uscita dal
metodo `@Transactional`, **marca la transazione outer come
rollback-only** — cioe' la transazione del chunk Spring Batch, che
avvolge reader + processor + writer.

Il fatto che `EnteApiService.notifyRendicontazione` intercetti poi
l'eccezione in `catch (IllegalStateException | IllegalArgumentException e)`
e ritorni normalmente **non annulla il rollback-only**: il flag e' gia'
stato impostato al momento in cui l'AOP interceptor ha osservato
l'uscita "in eccezione" del metodo `@Transactional`.

Al termine del chunk Spring Batch chiama commit, il transaction manager
vede il flag e solleva `UnexpectedRollbackException`. Lo step va in
`FAILED`, il job in `FAILED`, tutta la coda resta non elaborata fino
al successivo tick.

Il pattern e' documentato in Spring Reference §17.5.7 ("Global rollback
rules") e la semantica di default e': **qualsiasi `RuntimeException`
uscente da un metodo `@Transactional` marca la trans corrente per
rollback**, indipendentemente da chi la intercetti a monte.

## Fix

Le due chiamate a `ConnettoreService` in `EnteApiService` sono state
wrappate in helper che eseguono la lookup dentro un `TransactionTemplate`
con `PROPAGATION_NOT_SUPPORTED`:

```java
private final TransactionTemplate txNotSupported;

public EnteApiService(..., PlatformTransactionManager transactionManager) {
    // ...
    this.txNotSupported = new TransactionTemplate(transactionManager);
    this.txNotSupported.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
}

private Connettore getConnettoreIsolated(String codConnettore) {
    return txNotSupported.execute(status -> connettoreService.getConnettore(codConnettore));
}

private Map<String, String> getConnettoreAsMapIsolated(String codConnettore) {
    return txNotSupported.execute(status -> connettoreService.getConnettoreAsMap(codConnettore));
}
```

Semantica di `NOT_SUPPORTED`: **sospende la transazione outer** per la
durata dell'`execute(...)`. Dentro il template, quando
`ConnettoreService.getConnettore` viene chiamata con la sua
`@Transactional(readOnly = true)`, Spring apre una **nuova transazione
temporanea** — perche' non c'era piu' una trans attiva da propagare.
Se `getConnettore` lancia, quella trans temporanea viene marcata
rollback-only e chiusa **immediatamente** all'uscita dal template.
Al ripristino della trans outer del chunk, il flag rollback-only non
c'e' piu': la trans e' pulita e il chunk puo' committare.

Le chiamate wrappate sono:

- `getBaseUrl()` → `connettoreService.getConnettore(code).getUrl()` diventa `getConnettoreIsolated(code).getUrl()`
- `validateConnectorVersion()` → `connettoreService.getConnettoreAsMap(code)` diventa `getConnettoreAsMapIsolated(code)`
- `getOrCreateApi()`.computeIfAbsent → `connettoreService.getConnettore(code)` diventa `getConnettoreIsolated(code)` (per consistenza; qui non e' strettamente necessario perche' il record e' gia' passato dai preflight, ma isolarlo protegge da regressioni future)

Non sono invece wrappate:

- `connettoreService.getRestTemplate(code)` — non lancia eccezioni "trans-marker" in caso di connettore mancante; la lookup fallisce prima in `getConnettore`
- `connettoreService.clearCache()` — no-op transazionale
- `applicazioneRepository.findByCodApplicazione(...)` — Spring Data JPA repository: ritorna `Optional.empty()` senza sollevare, quindi non contamina la trans outer.

### Test di regressione

Nuovo test `connettoreLookupIsolatedFromOuterTransaction` in
`EnteApiServiceTest`: mocca `PlatformTransactionManager` e verifica che
`getTransaction(def)` venga invocato per ogni lookup del connettore
(cioe' che il `TransactionTemplate` sia effettivamente attraversato).
Se qualcuno reintroduce una chiamata diretta a
`connettoreService.getConnettore(...)` fuori dal template, il verify
fallisce prima che il bug possa arrivare in produzione.

Gli altri test `notifySkipsWhen*` sono stati aggiornati al nuovo
costruttore (dipendenza aggiuntiva su `PlatformTransactionManager`)
con stubbing `lenient()` perche' non tutti i test arrivano a invocare
il template (alcuni falliscono prima su applicazione mancante).

## Impatto operativo

- Deployando la `1.0.8` sopra la `1.0.7` in produzione, il job smettera'
  di finire in `FAILED` su record con connettore non idoneo.
- Le rendicontazioni per applicazioni **sane** (connettore configurato
  su `REST_2`, abilitato, con l'URL) verranno finalmente elaborate
  nel run successivo, invece di restare in coda.
- I record con connettore non idoneo continueranno a essere marcati
  `KO` dal processor e a **restare pending** per un retry al tick
  successivo: appena l'operatore sistema la configurazione (creazione
  del connettore, `ABILITATO=true`, `VERSIONE=REST_2`), la notifica
  parte automaticamente.

## Compatibilita'

Nessun cambio di schema DB, nessuna nuova property, nessun cambio di
env-var. Chi sta su `1.0.7` puo' aggiornare a `1.0.8` cambiando solo
il tag immagine.

## Dipendenze principali

Invariate rispetto a `1.0.7`.

| Artifact                                              | Versione        |
| ----------------------------------------------------- | --------------- |
| `org.gov4j.govpay:govpay-bom` (parent)                | `1.1.3`         |
| `org.gov4j.govpay:govpay-common`                      | `1.1.2`         |
| `org.gov4j.govpay:govpay-ec-client`                   | `1.0.1`         |
| `com.fasterxml.jackson:jackson-bom` (override)        | `2.21.4`        |
| `io.netty:netty-bom` (override)                       | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55`       |
| `ch.qos.logback:logback-classic` / `logback-core` (override) | `1.5.35`  |

## Asset di release

| File                              | Descrizione                                                                             |
| --------------------------------- | --------------------------------------------------------------------------------------- |
| `govpay-notify-batch-1.0.8.jar`   | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`)               |
| `sql.zip`                         | Script SQL di inizializzazione (se aggiornati)                                          |
| `release-reports-1.0.8.zip`       | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline                      |

## Riferimenti

- ChangeLog: voci datate 2026-07-15 nel file [`ChangeLog`](ChangeLog).
- Commit precedente (`1.0.7`): tag `1.0.7` (`bab5da7`).
