# GovPay NOTIFY Batch — Release Notes v1.0.6

Data: 2026-07-02
Branch: `1.x.x`
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Release di manutenzione: bump di `govpay-ec-client` alla `1.0.1`, che
corregge il codegen rotto di `NuovaRendicontazione.EsitoEnum`. Chiude
la *Limitazione nota* citata nelle RELEASE_NOTES dalla `1.0.1` in poi:
il path HTTP di notifica delle rendicontazioni e' ora effettivamente
eseguibile a runtime.

## Contesto

Nelle immagini `1.0.1` … `1.0.5` il primo tentativo di serializzare
una `NuovaRendicontazione` triggerava:

```
java.lang.ExceptionInInitializerError
Caused by: java.lang.NumberFormatException: Character n is neither
  a decimal digit number, decimal point, nor "e" notation exponential mark.
  at java.math.BigDecimal.<init>
  at NuovaRendicontazione$EsitoEnum.<clinit>(NuovaRendicontazione.java:74)
```

perche' il codegen di `govpay-ec-client:1.0.0` aveva dichiarato le
costanti dell'enum come `new BigDecimal("null")`. Il batch partiva ma
falliva ogni run che avesse almeno una riga nella query
`RtNotifyReader` (in produzione: sempre, appena la coda si popolava).

## Dipendenze

- **`org.gov4j.govpay:govpay-ec-client`** `1.0.0` → `1.0.1`.

Differenze rilevanti nel client:

- Costanti dell'enum ora nominali: `ESEGUITO`, `REVOCATO`,
  `ESEGUITO_STANDIN`, `ESEGUITO_STANDIN_SENZA_RPT`, `ESEGUITO_SENZA_RPT`
  (al posto di `NUMBER_null`, `NUMBER_null2`, ...).
- `<clinit>` sano, l'enum si inizializza correttamente.
- Signature: `fromValue(String)` invece di `fromValue(BigDecimal)`.

## Fix

### `EnteApiService`

Sostituita la chiamata precedente:

```java
rndInfo.setEsito(EsitoEnum.fromValue(BigDecimal.valueOf(rtInfo.getEsito())));
```

con un helper privato che mappa il codice esito numerico letto dal DB
sulle costanti nominali del nuovo enum:

| Codice DB | Enum |
|---|---|
| `0` | `ESEGUITO` |
| `3` | `REVOCATO` |
| `4` | `ESEGUITO_STANDIN` |
| `8` | `ESEGUITO_STANDIN_SENZA_RPT` |
| `9` | `ESEGUITO_SENZA_RPT` |

Codici sconosciuti sollevano `IllegalArgumentException` (record
marcato in errore dal processor, gli altri della run proseguono).

## Compatibilita'

Nessun cambio di firma di env-var o di property. Chi sta su `1.0.5`
puo' aggiornare a `1.0.6` cambiando solo il tag immagine.

## Dipendenze principali

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | **`1.0.1`** |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

## Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.6.jar` | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`) |
| `sql.zip` | Script SQL di inizializzazione (se aggiornati) |
| `release-reports-1.0.6.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

## Riferimenti

- ChangeLog: voci datate 2026-07-02 nel file [`ChangeLog`](ChangeLog).
- Commit precedente (`1.0.5`): `be5abfb`.
- Upstream: `org.gov4j.govpay:govpay-ec-client:1.0.1` (Maven Central).
