<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Notify Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-notify-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-notify-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-notify-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-notify-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-notify-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot per la notifica delle ricevute di rendicontazione (RT) alle applicazioni
dell'Ente Creditore. Il batch individua le rendicontazioni acquisite ma non ancora notificate
all'applicazione di pertinenza e le invia tramite chiamata REST verso l'endpoint configurato
sul connettore dell'applicazione.

## Architettura Implementata

Il batch e' composto da un singolo job (`rtNotifyJob`) con un unico step chunk-oriented
(`rtNotifyStep`) che processa le rendicontazioni da notificare.

### Step: Notifica Rendicontazioni
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

Tutti gli eventi delle chiamate (richiesta, risposta, eccezioni) sono tracciati sul
**Giornale Degli Eventi (GDE)** tramite `GdeService`.

## Entita' Database

Il batch lavora su entita' GovPay pre-esistenti. Le tabelle principali coinvolte sono:

### APPLICAZIONI
- Anagrafica delle applicazioni dell'Ente Creditore.
- Campo `cod_connettore_integrazione`: chiave del connettore RT da utilizzare per la notifica.

### FR
- Flussi di rendicontazione acquisiti dal sistema FDR di pagoPA.

### RENDICONTAZIONI
- Singoli pagamenti rendicontati all'interno di un FR. Il batch elabora gli item che
  risultano da notificare.
- Relazione opzionale con `VERSAMENTI` e quindi con `APPLICAZIONI`.

### VERSAMENTI / SINGOLI_VERSAMENTI
- Tabelle pre-esistenti GovPay per le posizioni debitorie e i singoli versamenti.

### CONNETTORI
- Configurazione delle API esterne (URL, credenziali, timeout).
- Risolto da `ConnettoreService` (libreria `govpay-common`).

## Configurazione

### Connessione API Ente

La configurazione delle API REST dell'Ente (URL, credenziali, timeout) e' gestita tramite
la tabella `CONNETTORI` del database GovPay, attraverso la libreria `govpay-common`.

Ogni applicazione ha il proprio connettore RT configurato nel campo
`cod_connettore_integrazione` della tabella `APPLICAZIONI`. Il batch risolve il connettore
per ogni rendicontazione partendo dall'applicazione associata.

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

# Intervallo di scheduling (ms, default: 2 ore)
scheduler.rtNotifyJob.fixedDelayString=7200000

# Ritardo iniziale prima della prima esecuzione (ms)
scheduler.initialDelayString=1
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

# Esecuzione singola (profilo cron - esegue una volta e termina)
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
Il job puo' essere avviato manualmente tramite endpoint REST esposti da `BatchController`:
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

# Svuotamento cache connettori
curl http://localhost:8080/api/batch/clearCache
```

## Caratteristiche Implementate

### Autenticazione API
- Autenticazione configurata per ogni connettore nella tabella `CONNETTORI` del database GovPay.
- Supporto multi-applicazione con credenziali diverse per ciascun connettore.

### Caching dei client API
- Le istanze `NotificaRendicontazioniApi` sono cachate per codice connettore in
  `EnteApiService`, evitando la creazione ripetuta di client REST per applicazioni che
  condividono lo stesso connettore.
- La cache puo' essere svuotata via endpoint `/api/batch/clearCache`.

### Gestione errori HTTP
- `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` e altri errori HTTP vengono
  tracciati come eventi KO sul GDE con dettaglio dello status code, e lo step prosegue
  sull'item successivo.
- Errori di marshalling JSON vengono tracciati come KO con esito 400.

### Multi-nodo e Recovery
- Gestione esecuzioni su cluster multi-nodo tramite `JobConcurrencyService` di `govpay-common`.
- Lock distribuito su tabelle Spring Batch.
- Recovery automatico job bloccati (timeout: 120 minuti configurabile).
- Prevenzione esecuzioni concorrenti.

### Integrazione GDE
- Tracciamento eventi nel Giornale Degli Eventi (GDE) per ogni notifica.
- Payload completo delle richieste/risposte API (header, body, status).
- Categoria evento `INTERFACCIA`, ruolo `CLIENT`, componente `API_ENTE`.

### Date flessibili in ingresso
- Deserializer custom (`OffsetDateTimeDeserializer`, `LocalDateFlexibleDeserializer`)
  in grado di accettare formati con millisecondi a lunghezza variabile (1-9 cifre) e
  con/senza timezone, per tolleranza alle varianti restituite dalle API esterne.

## Logging

Il livello di log puo' essere configurato in `application.properties`:

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

La copertura del codice e' verificata in pipeline tramite **JaCoCo** e **SonarCloud**.

## Note Tecniche

- **Java Version**: 21 (richiesto da Spring Boot 3.5.7)
- **Spring Boot**: 3.5.7
- **Spring Batch**: 5.x (incluso in Spring Boot Starter)
- **Database**: PostgreSQL (prod), MySQL, Oracle, SQL Server, H2 (dev)
- **Build Tool**: Maven 3.6.3+

## Documentazione

- **[ChangeLog](ChangeLog)** - Storia completa delle modifiche e release
- **[RELEASE_NOTES.md](RELEASE_NOTES.md)** - Note di rilascio per versione

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
