# GovPay NOTIFY Batch — Release Notes v1.0.7

Data: 2026-07-13
Branch: `1.x.x`
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Release di manutenzione: hotfix di runtime sul reader del job
`rtNotifyJob`, che falliva ogni run in presenza di celle `NULL` sulle
colonne opzionali. Nessun cambio funzionale, dipendenze invariate
rispetto a `1.0.6`.

## Contesto

Nella `1.0.6` (e prima), il `RtNotifyReader.initToBeNotify()` iterava le
righe della query `findRendicontazioneWithNoPagamentoAfterId` e mappava
ogni colonna con un helper del tipo:

```java
private OffsetDateTime convertToOffsetDateTime(Object object) {
    if (object instanceof OffsetDateTime dateValue) return dateValue;
    if (object instanceof LocalDateTime  dateValue) return dateValue.atZone(...).toOffsetDateTime();
    throw new IllegalArgumentException("... " + object.getClass().getName());
}
```

Se una cella del ResultSet era `NULL` (es. `cod_bic_riversamento`,
`iur` della `fr`, ecc.), il flusso cadeva nel `throw` — e prima di
lanciarlo tentava `object.getClass().getName()` su un riferimento
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

## Fix

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

## Fix — Errori di configurazione della singola rendicontazione non piu' fatali

Precedentemente, se una delle righe processate faceva parte di:

- un'applicazione **non trovata** (`applicazioneRepository.findByCodApplicazione` ritorna `Optional.empty()`),
- un'applicazione con **`cod_connettore_integrazione` null o blank**,
- un **connettore inesistente o disabilitato** (`ABILITATO=false` in
  `connettori`),

`EnteApiService` propagava `IllegalStateException` / `IllegalArgumentException`
al `RtNotifyProcessor`. Con lo step configurato senza `skipPolicy` e
`chunk-size=1`, la prima riga problematica faceva:

- rollback del chunk,
- `rtNotifyStep` → `FAILED`,
- `rtNotifyJob` → `FAILED`.

Effetto: **tutte le altre rendicontazioni pendenti** (comprese quelle
di applicazioni sane) **restavano non elaborate** fino al successivo
tick del cron — dove il ciclo si ripeteva, con lo stesso esito.

### Come si comporta ora

Aggiunto in `notifyRendicontazione` un catch dedicato
`catch (IllegalStateException | IllegalArgumentException e)` che:

1. logga a `WARN` con contesto (rtId, taxCode, iur, iuv, messaggio
   originale della configurazione — es. `"Connettore non abilitato: XYZ"`);
2. completa lo `statusCodeFuture` con `HttpStatus.SERVICE_UNAVAILABLE`,
   cosicche' il processor esiti la riga come **`KO`**;
3. **non invia alcun evento al GDE**: la config non idonea significa
   che non c'e' stata alcuna interazione con l'ente (nessuna request
   HTTP inviata), quindi non c'e' nulla da tracciare come evento di
   comunicazione — la segnalazione resta solo nei log applicativi;
4. ritorna una stringa che inizia con `"Configurazione non idonea: "`,
   rintracciabile nei log applicativi.

Il writer chiama `registerNotificaRt` solo su esito `OK`, quindi la
riga in `notifiche` **resta pending**: verra' ripescata al tick
successivo del cron e reinviata automaticamente non appena la
configurazione sara' corretta, senza intervento manuale.

Il job continua a processare tutte le righe della coda. Al termine il
run risulta `COMPLETED` con eventuali `KO` per le righe misconfigurate,
senza far saltare le altre.

### Nuova precondizione: versione API del connettore

Alla lista di verifiche di "idoneita' del connettore" e' stata aggiunta
la **versione dell'API di integrazione**. Il batch supporta unicamente
la versione **v2** (`POST/PUT` sulle risorse `/ricevute` e
`/rendicontazioni`); la v1 storica di govpay-core (SOAP + POST
`/pagamenti`) non e' implementata.

Su `govpay-common:1.1.2` il campo `versione` non e' ancora esposto
come attributo tipizzato del modello `Connettore` (arriva su main con
la 2.x). Fino ad allora la property viene letta tramite
`ConnettoreService.getConnettoreAsMap(codConnettore)` e verificata
contro la proprieta' `VERSIONE` della tabella `connettori`.

Casistica gestita:

| Contenuto property `VERSIONE`       | Comportamento                          |
| ----------------------------------- | -------------------------------------- |
| Assente / `null` / stringa blank    | KO configurazione non idonea, prosegue |
| `REST_2`                            | Notifica eseguita normalmente          |
| `REST_1` (v1 legacy)                | KO configurazione non idonea, prosegue |
| Qualunque altro valore              | KO configurazione non idonea, prosegue |

Il messaggio di log include il codice del connettore, il valore
trovato e quello atteso, ad es.:

```
Notifica saltata per rtId 42 (taxCode 12345678901, iur IUR..., iuv 01...)
 - configurazione non idonea:
   Versione API del connettore CONN_ENTE_XYZ non supportata:
   'REST_1' (attesa 'REST_2')
```

## Compatibilita'

Nessun cambio di firma di env-var o di property. Chi sta su `1.0.6`
puo' aggiornare a `1.0.7` cambiando solo il tag immagine. Non serve
alcuna modifica al DB o alla configurazione dei connettori.

## Dipendenze principali

Invariate rispetto a `1.0.6`.

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.1` |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

## Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.7.jar` | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`) |
| `sql.zip` | Script SQL di inizializzazione (se aggiornati) |
| `release-reports-1.0.7.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

## Riferimenti

- ChangeLog: voci datate 2026-07-13 nel file [`ChangeLog`](ChangeLog).
- Commit precedente (`1.0.6`): `7a97cb8` (tag `1.0.6`).
