# GovPay NOTIFY Batch — Release Notes v1.0.7

Data: 2026-07-14
Branch: `1.x.x`
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Release di manutenzione, hotfix di runtime al job `rtNotifyJob`.
Interviene su due punti che, nella `1.0.6`, potevano portare il job
in stato `FAILED` gia' al primo record processato, bloccando l'invio
di tutte le rendicontazioni pendenti fino al successivo tick del cron:

1. **NPE nel reader** su celle `NULL` del `ResultSet` (colonne opzionali).
2. **Errori di configurazione** della singola rendicontazione
   (applicazione, connettore, versione API) che propagavano fino allo
   step chunk-oriented facendo abortire l'intero job.

Nessun cambio funzionale sull'API di integrazione, nessuna modifica
di schema DB o di property. Dipendenze invariate rispetto a `1.0.6`.

---

## Fix 1 — `RtNotifyReader` NPE su celle `NULL`

### Cosa succedeva

Nella `1.0.6` (e prima), il `RtNotifyReader.initToBeNotify()` iterava
le righe della query `findRendicontazioneWithNoPagamentoAfterId` e
mappava ogni colonna con un helper del tipo:

```java
private OffsetDateTime convertToOffsetDateTime(Object object) {
    if (object instanceof OffsetDateTime dateValue) return dateValue;
    if (object instanceof LocalDateTime  dateValue) return dateValue.atZone(...).toOffsetDateTime();
    throw new IllegalArgumentException("... " + object.getClass().getName());
}
```

Se una cella del `ResultSet` era `NULL` (es. `cod_bic_riversamento`,
`iur` della `fr`, ecc.) il flusso cadeva nel `throw` e — prima di
lanciarlo — tentava `object.getClass().getName()` su un riferimento
`null`, sollevando `NullPointerException` e facendo abortire il
`beforeStep`:

```
Caused by: java.lang.NullPointerException: Cannot invoke "Object.getClass()" because "object" is null
    at it.govpay.notify.batch.tasklet.RtNotifyReader.convertToOffsetDateTime(RtNotifyReader.java:107)
    at it.govpay.notify.batch.tasklet.RtNotifyReader.initToBeNotify(RtNotifyReader.java:63)
    ...
Encountered an error executing step rtNotifyStep in job rtNotifyJob
```

Effetto in produzione: il job segnava **FAILED** ad ogni giro, senza
mai processare nulla.

### Fix

Aggiunto guard iniziale `if (object == null) return null;` a tutti e
quattro gli helper di `RtNotifyReader`:

- `convertToLong`
- `convertToInteger`
- `convertToBigDecimal`
- `convertToOffsetDateTime`

Le colonne opzionali che possono legittimamente essere `NULL` (bic
riversamento, iur, revisione, date su FR non ancora popolate, ecc.)
ora si propagano come `null` nel `RtNotifyContext` senza far esplodere
il reader. Le colonne obbligatorie continuano a comportarsi come prima
(il valore c'e' e il pattern matching produce il tipo giusto).

Piccolo cleanup collaterale: sistemato lo spazio mancante nel messaggio
d'errore di `convertToLong` (`"... to long" + name` → `"... to long " + name`).

---

## Fix 2 — Config non idonea non fa piu' fallire il job

### Cosa succedeva

Se una delle righe processate faceva parte di:

- un'applicazione **non trovata** (`applicazioneRepository.findByCodApplicazione` ritorna `Optional.empty()`),
- un'applicazione con **`cod_connettore_integrazione` null o blank**,
- un **connettore inesistente o disabilitato** (`ABILITATO=false` in
  `connettori`, `IllegalArgumentException` sollevata da
  `ConnettoreService`),

`EnteApiService` propagava `IllegalStateException` /
`IllegalArgumentException` al `RtNotifyProcessor`. Con lo step
configurato senza `skipPolicy` e `chunk-size=1`, la prima riga
problematica faceva:

- rollback del chunk,
- `rtNotifyStep` → `FAILED`,
- `rtNotifyJob` → `FAILED`.

Effetto: **tutte le altre rendicontazioni pendenti** (comprese quelle
di applicazioni sane) **restavano non elaborate** fino al successivo
tick del cron — dove il ciclo si ripeteva, con lo stesso esito. Una
sola app misconfigurata bloccava di fatto tutta la coda.

### Nuova precondizione: versione API del connettore

Alla lista di verifiche di "idoneita' del connettore" e' stata aggiunta
la **versione dell'API di integrazione**. Il batch supporta unicamente
la versione **v2** (`PUT /ricevute`, `POST /rendicontazioni`); la v1
storica di govpay-core (SOAP + `POST /pagamenti`) **non e' implementata**
in questo componente.

Su `govpay-common:1.1.2` il campo `versione` non e' ancora esposto
come attributo tipizzato del modello `Connettore` (arriva su main con
la 2.x). Fino ad allora la property viene letta tramite
`ConnettoreService.getConnettoreAsMap(codConnettore)` e verificata
contro la proprieta' `VERSIONE` della tabella `connettori`.

Casistica gestita:

| Contenuto property `VERSIONE`    | Comportamento                          |
| -------------------------------- | -------------------------------------- |
| Assente / `null` / stringa blank | KO configurazione non idonea, prosegue |
| `REST_2`                         | Notifica eseguita normalmente          |
| `REST_1` (v1 legacy)             | KO configurazione non idonea, prosegue |
| Qualunque altro valore           | KO configurazione non idonea, prosegue |

### Come si comporta ora

Aggiunto in `notifyRendicontazione` un catch dedicato
`catch (IllegalStateException | IllegalArgumentException e)` che
copre **tutte** le casistiche di configurazione non idonea (app
mancante, connettore mancante/blank, connettore disabilitato,
versione API non supportata):

1. logga a `WARN` con contesto — `rtId`, `taxCode`, `iur`, `iuv` e
   messaggio originale (es. `"Connettore non abilitato: CONN_XYZ"` o
   `"Versione API del connettore CONN_XYZ non supportata: 'REST_1' (attesa 'REST_2')"`);
2. completa lo `statusCodeFuture` con `HttpStatus.SERVICE_UNAVAILABLE`,
   cosicche' il processor esiti la riga come **`KO`**;
3. **non invia alcun evento al GDE**: la config non idonea significa
   che non c'e' stata alcuna interazione con l'ente (nessuna request
   HTTP inviata), quindi non c'e' nulla da tracciare come evento di
   comunicazione — la segnalazione resta solo nei log applicativi;
4. ritorna una stringa che inizia con `"Configurazione non idonea: "`,
   rintracciabile nei log applicativi via il writer.

Il writer chiama `registerNotificaRt` **solo su esito `OK`**, quindi
la riga in `notifiche` **resta pending**: verra' ripescata al tick
successivo del cron e reinviata automaticamente non appena la
configurazione sara' corretta, senza intervento manuale.

Il job continua a processare tutte le righe della coda. Al termine il
run risulta `COMPLETED` con eventuali `KO` per le righe misconfigurate,
senza far saltare le altre.

### Riepilogo del comportamento

| Situazione                                             | Log | Esito processor | Marcata inviata in DB | Evento GDE |
| ------------------------------------------------------ | --- | --------------- | --------------------- | ---------- |
| Notifica HTTP `2xx`                                    | `INFO` | `OK` | ✅ | ✅ ok |
| Notifica HTTP `4xx` (400/401/403)                      | `WARN` | `OK` | ✅ (bruciata) | ✅ ko |
| Notifica HTTP `5xx` / errore di rete                   | `ERROR` | `KO` | ❌ (retry) | ✅ ko |
| Config non idonea (app/connettore/versione) **[NEW]**  | `WARN` | `KO` | ❌ (retry) | ❌ nessuno |

### Esempio di log

```
Notifica saltata per rtId 42 (taxCode 12345678901, iur IUR..., iuv 01...)
 - configurazione non idonea:
   Versione API del connettore CONN_ENTE_XYZ non supportata:
   'REST_1' (attesa 'REST_2')
```

---

## Compatibilita'

Nessun cambio di firma di env-var o di property. Chi sta su `1.0.6`
puo' aggiornare a `1.0.7` cambiando solo il tag immagine. Non serve
alcuna modifica al DB o alla configurazione dei connettori.

Attenzione: con la 1.0.7 le rendicontazioni destinate a connettori
configurati su `VERSIONE=REST_1` **restano nella coda** invece di
essere bruciate. Se una piattaforma ha applicazioni legacy che si
aspettavano l'invio via v1, e' opportuno:

- migrare il connettore a `VERSIONE=REST_2` (l'ente deve esporre
  l'API v2), oppure
- disabilitare il connettore (`ABILITATO=false`) se l'app non deve
  essere piu' notificata.

## Dipendenze principali

Rispetto alla `1.0.6` sono invariate ad eccezione di `logback` (bumpato
da `1.5.28` a `1.5.35` per risolvere due CVE, vedi sotto).

| Artifact                                              | Versione        |
| ----------------------------------------------------- | --------------- |
| `org.gov4j.govpay:govpay-bom` (parent)                | `1.1.3`         |
| `org.gov4j.govpay:govpay-common`                      | `1.1.2`         |
| `org.gov4j.govpay:govpay-ec-client`                   | `1.0.1`         |
| `com.fasterxml.jackson:jackson-bom` (override)        | `2.21.4`        |
| `io.netty:netty-bom` (override)                       | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55`       |
| `ch.qos.logback:logback-classic` / `logback-core` (override) | `1.5.35`  |

## CVE risolte in questa release

Bump di `logback` per due CVE segnalate da OSV scan (entrambe risolte
elevando `logback-classic` e `logback-core` alla `1.5.35`):

| Advisory | CVSS | Artifact | Fix version |
| --- | --- | --- | --- |
| [GHSA-jhq6-gfmj-v8fx](https://osv.dev/GHSA-jhq6-gfmj-v8fx) | 2.9 | `ch.qos.logback:logback-core` | `1.5.35` |
| [GHSA-p47f-322f-whfh](https://osv.dev/GHSA-p47f-322f-whfh) | 1.2 | `ch.qos.logback:logback-core` | `1.5.33` |

Il parent `spring-boot-dependencies` (via `govpay-bom` 1.1.3) importava
`1.5.28`; sono state aggiunte una property `logback.version` e voci di
`dependencyManagement` per entrambi gli artifact — stesso pattern gia'
usato per `jackson-bom`, perche' la sola property viene sovrascritta
dal parent BOM.

## Asset di release

| File                              | Descrizione                                                                             |
| --------------------------------- | --------------------------------------------------------------------------------------- |
| `govpay-notify-batch-1.0.7.jar`   | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`)               |
| `sql.zip`                         | Script SQL di inizializzazione (se aggiornati)                                          |
| `release-reports-1.0.7.zip`       | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline                      |

## Riferimenti

- ChangeLog: voci datate 2026-07-13 nel file [`ChangeLog`](ChangeLog).
- Commit precedente (`1.0.6`): tag `1.0.6` (`7a97cb8`).
- Commit head della 1.0.7: `6b61fbd` sul branch `1.x.x`.
