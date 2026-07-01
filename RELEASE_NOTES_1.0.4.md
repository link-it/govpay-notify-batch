# GovPay NOTIFY Batch — Release Notes v1.0.4

Data: 2026-07-01
Branch: `1.x.x`
Linea: 1.x (Spring Boot 3.5 + `govpay-common` 1.1.2)

Hotfix release sopra `1.0.3`: rimossa una `@Value` senza default che
faceva fail-fast dell'avvio quando la relativa property non era
impostata in ambiente. Nessun cambio funzionale, dipendenze invariate.

## Fix

### Placeholder `govpay.url` non risolvibile

Il campo `govpayUrl` di `GdeService` era iniettato via
`@Value("${govpay.url}")` senza default, ma non veniva mai letto da
nessun metodo. Dead code che faceva pero' fallire l'avvio con:

```
Could not resolve placeholder 'govpay.url' in value "${govpay.url}"
```

non appena la property non era impostata. Il problema si e' manifestato
al primo deploy dell'immagine `1.0.3` in ambiente locale (compose
senza `GOVPAY_URL` valorizzata). Rimosso il campo e il relativo import
da `GdeService`. Backport dal commit `d93d2bf` di `main`.

## Compatibilita'

Nessun cambio di firma di env-var o di property. Chi sta su `1.0.3`
puo' aggiornare a `1.0.4` cambiando solo il tag immagine. Se in
precedenza si era aggiunta `GOVPAY_URL` al deployment per aggirare il
fail-fast, la variabile puo' essere rimossa (era ignorata).

## Dipendenze principali

Invariate rispetto a `1.0.3`.

| Artifact | Versione |
| --- | --- |
| `org.gov4j.govpay:govpay-bom` (parent) | `1.1.3` |
| `org.gov4j.govpay:govpay-common` | `1.1.2` |
| `org.gov4j.govpay:govpay-ec-client` | `1.0.0` |
| `com.fasterxml.jackson:jackson-bom` (override) | `2.21.4` |
| `io.netty:netty-bom` (override) | `4.1.135.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` (override) | `10.1.55` |

## Limitazioni note (invariate da 1.0.1)

`NuovaRendicontazione.EsitoEnum` in `govpay-ec-client:1.0.0` ha le
costanti generate come `NUMBER_null`, `NUMBER_null2`, ... con valore
`new BigDecimal("null")` (bug del codegen). Il `<clinit>` della classe
fallisce con `NumberFormatException` al primo accesso, quindi il path
HTTP di `EnteApiService.notifyRendicontazione` resta non eseguibile a
runtime e non coperto dai test. Verra' affrontato con il rilascio di
una versione corretta di `govpay-ec-client`.

## Asset di release

| File | Descrizione |
| --- | --- |
| `govpay-notify-batch-1.0.4.jar` | Fat JAR eseguibile (driver JDBC esclusi, vanno forniti via `LOADER_PATH`) |
| `sql.zip` | Script SQL di inizializzazione (se aggiornati) |
| `release-reports-1.0.4.zip` | Report OWASP, JaCoCo, OSV, SBOM, licenze e link al run di pipeline |

## Riferimenti

- ChangeLog: voci datate 2026-07-01 nel file [`ChangeLog`](ChangeLog).
- Commit precedente (`1.0.3`): `1ff8fc8`.
