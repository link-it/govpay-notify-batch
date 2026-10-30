<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Notify Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-notify-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-notify-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-notify-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-notify-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-notify-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot che invia alle applicazioni dell'Ente Creditore le notifiche di GovPay,
chiamando le API di integrazione dell'Ente configurate sul connettore dell'applicazione:

- **ricevute di pagamento** (RT): le notifiche `RICEVUTA` in coda nella tabella
  `NOTIFICHE`, inviate con `PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}`;
- **rendicontazioni**: i pagamenti rendicontati nei flussi FR e non ancora notificati,
  inviati con `POST /rendicontazioni`.

## Architettura Implementata

Il batch ha due job, ciascuno con un solo step chunk-oriented.

### Job `rtSendJob`: spedizione delle ricevute

Si attiva con `govpay.batch.rt-send.enabled=true` (default `false`) e parte ogni minuto
(`scheduler.rtSendJob.cron`), come nel monolite GovPay.

- **Reader**: `RtSendReader`
  - Legge una pagina di notifiche `RICEVUTA` in stato `DA_SPEDIRE` con data di prossima
    spedizione scaduta (`govpay.batch.rt-send.chunk-size`, default 50).
  - Per ogni notifica carica con govpay-common-pendenze la pendenza della RPT, con voci,
    opzione di pagamento e posizione debitoria, e le riscossioni della RPT.
- **Processor**: `RtSendProcessor`
  - Costruisce la ricevuta con `RicevutaV2Mapper` e `PendenzaPagataMapper` e la invia con
    `EnteRicevutaApiService`. Il connettore deve avere la versione `REST_2` dell'API EC.
- **Writer**: `RtSendWriter`
  - Aggiorna ogni notifica in una transazione propria (`RtSendStatusUpdater`):
    - spedita: stato `SPEDITO`;
    - errore non bloccante (errore HTTP, timeout, pendenza non migrata al modello v3):
      resta `DA_SPEDIRE`, con nuovo tentativo dopo tentativi² minuti, fino a un massimo
      di 24 ore;
    - errore definitivo (applicazione o connettore non configurati, versione dell'API
      non supportata, pendenza non trovata): stato `ANNULLATA`.

### Job `rtNotifyJob`: notifica delle rendicontazioni

Riparte 2 ore dopo la fine dell'esecuzione precedente (`scheduler.rtNotifyJob.fixedDelayString`).

- **Reader**: `RtNotifyReader`
  - Legge le rendicontazioni da notificare entro la finestra temporale configurata
    (`govpay.batch.finestra-temporale`, default 90 giorni).
  - Inizializza la lista in `BeforeStep` ed espone gli item al chunk.
- **Processor**: `RtNotifyProcessor`
  - Per ciascuna rendicontazione invoca `EnteApiService.notifyRendicontazione`, che
    risolve il connettore dell'applicazione e chiama l'API dell'Ente con
    `POST /rendicontazioni`.
  - Determina l'esito (`OK` / `KO`) sulla base dello status code restituito.
- **Writer**: `RtNotifyWriter`
  - Aggiorna lo stato delle rendicontazioni notificate.
  - Logga l'esito per ciascun item.

Gli eventi delle chiamate di questo job (richiesta, risposta, eccezioni) sono tracciati
sul **Giornale Degli Eventi (GDE)** tramite `GdeService`.

## Requisiti

- **GovPay 3.11.0 o successivo.** Il batch legge le pendenze nel modello dati v3
  (posizioni debitorie, opzioni di pagamento, soggetti debitori) e non gira su un
  database GovPay 3.10.
- Le pendenze si leggono con la libreria **govpay-common-pendenze**, le anagrafiche e
  i connettori con **govpay-common**.

## Entita' Database

Il batch non possiede schema: lavora sulle tabelle del database GovPay.

Lette e aggiornate con entita' proprie del batch:

| Tabella | Uso |
|---|---|
| `NOTIFICHE` | Coda di spedizione delle ricevute: il batch legge le notifiche `RICEVUTA` da spedire e ne aggiorna stato, tentativi e prossima spedizione. |
| `RPT` | Dati della ricevuta notificata all'Ente (esito, importo, attestante, XML della RT). |
| `FR` | Flussi di rendicontazione acquisiti dal sistema FDR di pagoPA. |
| `RENDICONTAZIONI` | Pagamenti rendicontati: il batch notifica quelli senza pagamento associato e ne registra la notifica (`notifica_inviata`). |
| `APPLICAZIONI` | Applicazione destinataria; `cod_connettore_integrazione` indica il connettore da usare. |

Lette con govpay-common-pendenze (modello v3):

| Tabella | Uso |
|---|---|
| `VERSAMENTI` | La pendenza pagata. |
| `SINGOLI_VERSAMENTI` | Le voci della pendenza; per le rendicontazioni, il collegamento all'applicazione. |
| `DOCUMENTI` | La posizione debitoria: documento e causale di default della ricevuta. |
| `OPZIONI_PAGAMENTO` | Tipologia dell'opzione (soluzione unica, rata, soglia) e giorni. |
| `SOGGETTI_DEBITORI` | Il soggetto pagatore, quello con `ordine = 0`. |
| `PAGAMENTI` | Le riscossioni della RPT, una per voce pagata. |

Lette con govpay-common:

| Tabella | Uso |
|---|---|
| `DOMINI`, `UO`, `TIPI_VERSAMENTO`, `TRIBUTI`, `TIPI_TRIBUTO`, `IBAN_ACCREDITO` | Codici e descrizioni della sezione `pendenza` della ricevuta. |
| `CONNETTORI` | Configurazione delle API dell'Ente (URL, credenziali, versione), risolta da `ConnettoreService`. |
| `CONFIGURAZIONE` | Livelli di log modificabili a runtime (riga `log_level`). |
| `BATCH_*` | Metadati delle esecuzioni Spring Batch e guardia anti-concorrenza fra nodi. |

Le pendenze scartate dalla migrazione al modello v3 (tabella `migrazione_v3_scarti`)
non hanno opzione di pagamento: la spedizione della loro ricevuta fallisce con un errore
non bloccante e riparte dopo la bonifica.

## Configurazione

### Connessione API Ente

La configurazione delle API REST dell'Ente (URL, credenziali, timeout) e' gestita tramite
la tabella `CONNETTORI` del database GovPay, attraverso la libreria `govpay-common`.

Ogni applicazione ha il proprio connettore configurato nel campo
`cod_connettore_integrazione` della tabella `APPLICAZIONI`. Il batch risolve il connettore
per ogni ricevuta e ogni rendicontazione partendo dall'applicazione destinataria.

### Parametri Batch
```properties
# Abilitazione batch
govpay.batch.enabled=true

# Identificativo del cluster per gestione multi-nodo
govpay.batch.cluster-id=GovPay-Notify-Batch

# Finestra temporale (giorni) entro cui considerare le rendicontazioni da notificare
govpay.batch.finestra-temporale=90

# Timeout (minuti) per rilevare esecuzioni bloccate
govpay.batch.stale-threshold-minutes=120

# Dimensione chunk per lo step rtNotifyStep
govpay.batch.notify-chunk-size=1

# Intervallo di scheduling di rtNotifyJob (ms, default: 2 ore)
scheduler.rtNotifyJob.fixedDelayString=7200000

# Ritardo iniziale prima della prima esecuzione (ms)
scheduler.initialDelayString=1

# Spedizione delle ricevute (rtSendJob): disabilitata di default
govpay.batch.rt-send.enabled=false

# Notifiche lette per esecuzione e dimensione del chunk di rtSendJob
govpay.batch.rt-send.chunk-size=50

# Timeout HTTP verso l'API dell'Ente (ms)
govpay.batch.rt-send.http.connect-timeout-ms=10000
govpay.batch.rt-send.http.read-timeout-ms=30000

# Schedulazione di rtSendJob (cron Spring a 6 campi, default ogni minuto)
scheduler.rtSendJob.cron=0 * * * * *

# Fuso orario delle colonne temporali; govpay-common-pendenze usa lo stesso
spring.jackson.time-zone=Europe/Rome
govpay.pendenze.fuso-orario=${spring.jackson.time-zone}
```

### Database
```properties
# Per sviluppo: H2 in-memory
spring.datasource.url=jdbc:h2:mem:notifydb
spring.datasource.driver-class-name=org.h2.Driver

# Per produzione: PostgreSQL
#spring.datasource.url=jdbc:postgresql://localhost:5432/govpaydb
#spring.datasource.driver-class-name=org.postgresql.Driver
#spring.datasource.username=govpay_user
#spring.datasource.password=your_password
```

### Metriche Prometheus

Gli endpoint `/actuator/health` e `/actuator/prometheus` rispondono di default sulla **stessa porta**
dell'applicazione (nessuna porta management separata è configurata). Per esporli su una porta dedicata
impostare:

```properties
management.server.port=[Porta dedicata per gli endpoint actuator]
```

oppure, in ambiente Docker, la variabile d'ambiente equivalente:

```bash
MANAGEMENT_SERVER_PORT=[Porta dedicata per gli endpoint actuator]
```

Se non valorizzata, gli endpoint restano sulla porta applicativa.

## Compilazione ed Esecuzione

### Compilazione
```bash
# Con Java 21 impostato come JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
mvn clean install
```

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e devono essere forniti esternamente a runtime.
Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato:

| Database   | Driver                                                                                     |
|------------|--------------------------------------------------------------------------------------------|
| PostgreSQL | [postgresql-42.7.9.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.9/) |
| MySQL      | [mysql-connector-j-9.6.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.6.0/) |
| Oracle     | [ojdbc11-23.26.1.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.1.0.0/) |
| SQL Server | [mssql-jdbc-12.8.2.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.2.jre11/) |
| H2         | [h2-2.4.240.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.4.240/)              |

### Esecuzione

Il jar utilizza `PropertiesLauncher` (layout ZIP) e richiede la proprieta' `loader.path`
per indicare la directory contenente i driver JDBC:

```bash
# Avvio applicazione (modalita' schedulata - profilo default)
java -Dloader.path=./jdbc-drivers -jar target/govpay-notify-batch.jar

# Esecuzione singola di rtNotifyJob (profilo cron - esegue una volta e termina)
java -Dloader.path=./jdbc-drivers -jar target/govpay-notify-batch.jar --spring.profiles.active=cron
```

### Esecuzione con Docker

Con Docker la variabile d'ambiente `LOADER_PATH` viene impostata automaticamente
dall'entrypoint alla directory `/opt/jdbc-drivers`. I driver devono essere montati
come volume o copiati nell'immagine:

```bash
# Montaggio volume con il driver JDBC
docker run -v ./jdbc-drivers:/opt/jdbc-drivers linkitaly/govpay-notify-batch
```

### Trigger Manuale via REST
I job possono essere avviati manualmente tramite endpoint REST esposti da `BatchController`.
`/run` avvia `rtNotifyJob` e la risposta riguarda solo questo job. Se la spedizione delle
ricevute e' abilitata, avvia anche `rtSendJob` in background, a meno che non sia gia' in
esecuzione.
```bash
# Avvio job
curl http://localhost:8080/api/batch/run

# Forzare esecuzione anche se in corso
curl http://localhost:8080/api/batch/run?force=true

# Stato corrente del batch
curl http://localhost:8080/api/batch/status

# Ultima esecuzione
curl http://localhost:8080/api/batch/lastExecution

# Prossima esecuzione stimata
curl http://localhost:8080/api/batch/nextExecution

# Svuotamento cache dei connettori di rtNotifyJob
curl http://localhost:8080/api/batch/clearCache
```

## Caratteristiche Implementate

### Autenticazione API
- Autenticazione configurata per ogni connettore nella tabella `CONNETTORI` del database GovPay.
- Supporto multi-applicazione con credenziali diverse per ciascun connettore.

### Caching dei client API
- I client REST sono cachati per codice connettore: `NotificaRendicontazioniApi` in
  `EnteApiService` e `NotificaRicevuteApi` in `EnteRicevutaApiService`. Applicazioni che
  condividono lo stesso connettore usano lo stesso client.
- L'endpoint `/api/batch/clearCache` svuota la cache di `EnteApiService`.

### Gestione errori HTTP
- `rtSendJob`: vedi gli esiti del writer in [Architettura](#architettura-implementata).
- `rtNotifyJob`: `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` e altri errori HTTP vengono
  tracciati come eventi KO sul GDE con dettaglio dello status code, e lo step prosegue
  sull'item successivo.
- Errori di marshalling JSON vengono tracciati come KO con esito 400.

### Multi-nodo e Recovery
- Gestione esecuzioni su cluster multi-nodo tramite `JobConcurrencyService` di `govpay-common`.
- Lock distribuito su tabelle Spring Batch.
- Recovery automatico job bloccati (timeout: 120 minuti configurabile).
- Prevenzione esecuzioni concorrenti.

### Integrazione GDE
- Tracciamento eventi nel Giornale Degli Eventi (GDE) per ogni notifica di rendicontazione.
- Payload completo delle richieste/risposte API (header, body, status).
- Categoria evento `INTERFACCIA`, ruolo `CLIENT`, componente `API_ENTE`.

### Date flessibili in ingresso
- Deserializer custom (`OffsetDateTimeDeserializer`, `LocalDateFlexibleDeserializer`)
  in grado di accettare formati con millisecondi a lunghezza variabile (1-9 cifre) e
  con/senza timezone, per tolleranza alle varianti restituite dalle API esterne.

## Logging

I livelli di log si leggono a runtime dalla riga `log_level` della tabella
`CONFIGURAZIONE`, condivisa con gli altri componenti GovPay: una modifica fatta dalla
console arriva al batch entro `LOG_LEVEL_REFRESH_INTERVAL` (default 60s). Ogni riga di log
riporta transaction id e correlation id dell'esecuzione.

I livelli iniziali si configurano in `application.properties`:

```properties
logging.level.it.govpay.notify.batch=DEBUG
logging.level.org.springframework.batch=INFO
logging.level.org.springframework.web.client=DEBUG
```

## Test

Il progetto include unit test per i principali componenti (reader, processor, writer,
service, controller, config, mapper, listener).

```bash
mvn test
```

`SchemaV3IntegrationTest` avvia con Testcontainers un PostgreSQL con lo schema di
GovPay 3.11.0 (`src/test/resources/db/postgresql/gov_pay-3.11.0.sql`) e valida le entita'
sullo schema reale: serve Docker. La copia dello schema va riallineata quando cambia
`gov_pay.sql` del core.

La copertura del codice e' verificata in pipeline tramite **JaCoCo** e **SonarCloud**.

## Note Tecniche

- **Java Version**: 21
- **Spring Boot**: 4.1.2
- **Spring Batch**: 6.x (incluso in Spring Boot Starter)
- **GovPay**: 3.11.0 o successivo
- **Database**: PostgreSQL (prod), MySQL, Oracle, SQL Server, H2 (dev)
- **Build Tool**: Maven 3.8.1+ (minimo imposto dal BOM GovPay)

## Documentazione

- **[ChangeLog](ChangeLog)** - Storia completa delle modifiche e release
- **[RELEASE_NOTES_2.0.0.md](RELEASE_NOTES_2.0.0.md)** - Note di rilascio della versione 2.0.0

## Contribuire

Per contribuire al progetto:
1. Fork del repository
2. Creare un branch per la feature (`git checkout -b feature/AmazingFeature`)
3. Commit delle modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Push del branch (`git push origin feature/AmazingFeature`)
5. Aprire una Pull Request

Assicurarsi di:
- Seguire lo stile di codifica del progetto
- Aggiungere test per nuove funzionalita'
- Aggiornare il ChangeLog seguendo il formato esistente
- Documentare le modifiche nel README se necessario

## License

Questo progetto e' distribuito sotto licenza GPL v3. Vedere il file [LICENSE](LICENSE) per i dettagli.

## Contatti

- **Progetto**: [GovPay Notify Batch](https://github.com/link-it/govpay-notify-batch)
- **Organizzazione**: [Link.it](https://www.link.it)

## Riconoscimenti

Questo progetto e' parte dell'ecosistema [GovPay](https://www.govpay.it) per la gestione dei pagamenti della Pubblica Amministrazione italiana tramite pagoPA.
