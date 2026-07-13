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
