<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Notify Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-notify-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-notify-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-notify-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-notify-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-notify-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che notifica alle applicazioni dell'Ente Creditore le rendicontazioni senza pagamento associato e, se abilitato, spedisce le ricevute di pagamento (RT).

## Sommario

Il batch esegue due job Spring Batch sul database di GovPay:

| Job | Default | Cosa fa |
|-----|---------|---------|
| `rtNotifyJob` | attivo, ogni 2 ore | Individua le rendicontazioni acquisite negli ultimi `govpay.batch.finestra-temporale` giorni che non hanno un pagamento in GovPay e non sono ancora state notificate, e le invia all'applicazione proprietaria della pendenza con `POST /rendicontazioni` dell'API di integrazione dell'Ente |
| `rtSendJob` | disattivo | Spedisce le ricevute di pagamento in coda nella tabella `notifiche` (tipo `RICEVUTA`, stato `DA_SPEDIRE`) con `PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}`, con nuovi tentativi a intervalli crescenti, come il thread di spedizione delle notifiche del monolite GovPay |

L'endpoint dell'Ente si ricava dal connettore di integrazione dell'applicazione, configurato in GovPay (tabella `connettori`): il batch supporta solo connettori con versione API `REST_2`. Le chiamate di `rtNotifyJob` sono registrate nel Giornale degli Eventi.

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest). Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge `fr`, `rendicontazioni`, `singoli_versamenti`, `versamenti`, `applicazioni`, `domini`, `rpt`, `connettori` e `configurazione`; aggiorna `rendicontazioni.notifica_inviata` e lo stato delle righe di `notifiche`. Le tabelle sono create dagli script di installazione di GovPay, il batch non crea lo schema |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori, entità di anagrafica, coordinamento dei job, API REST del batch, client GDE, transaction id e livelli di log da database |
| [govpay-ec-client](https://github.com/link-it/govpay-ec-client) | build | sì | Client generato dell'API di integrazione dell'Ente Creditore (`NotificaRendicontazioniApi`, `NotificaRicevuteApi`) |
| API di integrazione dell'Ente | runtime | sì | Servizio dell'Ente che riceve rendicontazioni e ricevute. URL, autenticazione e versione (`REST_2`) sono nel connettore di integrazione dell'applicazione |
| [govpay-fdr-batch](https://github.com/link-it/govpay-fdr-batch) | runtime | sì | Acquisisce da pagoPA i flussi di rendicontazione (`fr`, `rendicontazioni`) che `rtNotifyJob` notifica |
| GovPay (monolite) | runtime | no | Accoda in `notifiche` le ricevute che `rtSendJob` spedisce, con la stessa logica di stati e tentativi del thread di spedizione delle notifiche del monolite |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve gli eventi delle chiamate di `rtNotifyJob` (`POST /eventi`), se il connettore `govpay_gde_api` è abilitato in GovPay. Un GDE non raggiungibile non blocca il batch |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Può avviare il batch e consultarne le esecuzioni tramite l'API REST, se il batch è aggiunto al catalogo delle operazioni (`govpay.operazioni.catalogo`); nel catalogo di default non c'è |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install
```

Il risultato è il fat jar `target/govpay-notify-batch.jar`. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze; per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot (es. `govpay.batch.finestra-temporale` → `GOVPAY_BATCH_FINESTRATEMPORALE`, `scheduler.rtNotifyJob.fixedDelayString` → `SCHEDULER_RTNOTIFYJOB_FIXEDDELAYSTRING`). Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Notifica delle rendicontazioni (`rtNotifyJob`)

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.finestra-temporale` | `90` | Giorni, dalla data di acquisizione del flusso, entro cui una rendicontazione viene considerata |
| `govpay.batch.notify-chunk-size` | `1` | Rendicontazioni elaborate per transazione |
| `scheduler.rtNotifyJob.fixedDelayString` | `7200000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms (2 ore) |
| `scheduler.initialDelayString` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |
| `govpay.batch.enabled` | `true` | Letta in `BatchProperties` ma non usata: per non schedulare il job usare il profilo `cron` o un profilo diverso da `default` |

### Spedizione delle ricevute (`rtSendJob`)

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.rt-send.enabled` | `false` | Abilita il job: con `false` non viene schedulato e non parte dall'avvio manuale via REST |
| `govpay.batch.rt-send.chunk-size` | `50` | Notifiche lette e spedite per transazione |
| `govpay.batch.rt-send.http.connect-timeout-ms` | `10000` | Timeout di connessione verso l'Ente, in ms |
| `govpay.batch.rt-send.http.read-timeout-ms` | `30000` | Timeout di lettura della risposta dell'Ente, in ms |
| `scheduler.rtSendJob.cron` | `0 * * * * *` | Schedulazione (cron Spring a 6 campi: secondi, minuti, ore, giorno, mese, giorno della settimana); di default ogni minuto |

Esito della spedizione: con risposta 2xx la notifica passa a `SPEDITO`; con errore resta `DA_SPEDIRE` e viene ritentata dopo `tentativi² × 60` secondi, al massimo dopo 24 ore; se l'applicazione o il connettore non sono configurati, o la versione non è supportata, passa ad `ANNULLATA`.

### Esecuzione e cluster

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.cluster-id` | `GovPay-Notify-Batch` | Identificativo del nodo, registrato nelle esecuzioni; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.job-repository.jdbc.enabled` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, i job sono avviati dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `default` | `cron` per eseguire `rtNotifyJob` una volta e terminare (vedi [Esecuzione](#esecuzione)) |
| `server.port` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.application.name` | `govpay-notify-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `Europe/Rome` | Fuso orario dell'applicazione e delle date inviate all'Ente |

### Connettori e client HTTP

URL, autenticazione e versione delle API dell'Ente e del GDE non sono proprietà del batch: sono nei connettori di GovPay (tabella `connettori`), indicati dal campo `cod_connettore_integrazione` dell'applicazione e, per il GDE, dal connettore `govpay_gde_api`.

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.client.cache.enabled` | `false` | Carica i connettori all'avvio e li tiene in cache; la cache si svuota con `GET /api/batch/cache/clear` |
| `govpay.client.async.core-pool-size` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `async-http-` | Prefisso dei thread di invio |

### Database

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spring.datasource.url` | - | URL JDBC del database di GovPay (obbligatoria) |
| `spring.datasource.username` | - | Utente del database |
| `spring.datasource.password` | - | Password del database |
| `spring.datasource.driver-class-name` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.datasource.hikari.minimum-idle` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `logging.file.name` | - (solo console) | File di log; nel container `/var/log/govpay-notify/govpay-notify.log` |
| `logging.level.it.govpay` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.livelli-dinamici.enabled` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `60s` (variabile `LOG_LEVEL_REFRESH_INTERVAL`) | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `it.govpay` | Prefissi dei logger modificabili da database |

### Monitoraggio

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `management.server.port` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` (variabile `MANAGEMENT_ENDPOINTS`) | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `govpay-notify-batch` | Tag che identifica il servizio nelle metriche |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema Spring Batch

Prima del primo avvio, nel database di GovPay devono esistere le tabelle dei metadati Spring Batch: si creano con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`. L'immagine Docker lo esegue da sola se `GOVPAY_NOTIFY_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-notify-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo, esegue `rtNotifyJob` ogni `scheduler.rtNotifyJob.fixedDelayString` ms e, se abilitato, `rtSendJob` secondo `scheduler.rtSendJob.cron`; espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue `rtNotifyJob` una volta e termina: la pianificazione è affidata a un cron esterno. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log. `rtSendJob` non viene eseguito |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-notify-batch.jar --spring.profiles.active=cron
```

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-notify-batch](https://hub.docker.com/r/linkitaly/govpay-notify-batch) (release) e `linkitaly/govpay-notify-batch-dev` (sviluppo, a ogni push su `main`). Con lo scheduler interno il servizio risponde sulla porta `10001`; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-notify-batch -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  linkitaly/govpay-notify-batch
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile (es. `GOVPAY_BATCH_RTSEND_ENABLED=true`, `SCHEDULER_RTNOTIFYJOB_FIXEDDELAYSTRING=3600000`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_NOTIFY_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_NOTIFY_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_NOTIFY_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine job e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_NOTIFY_BATCH_INTERVALLO_CRON` | no | `120` | Minuti tra un'esecuzione e l'altra con lo scheduler interno. **Oggi senza effetto**: l'entrypoint la converte in `SCHEDULER_NOTIFYACQUISITIONJOB_FIXEDDELAYSTRING`, che il batch non legge; per cambiare l'intervallo usare `SCHEDULER_RTNOTIFYJOB_FIXEDDELAYSTRING` (in ms) |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_NOTIFY_GDE_URL` | no | - | **Oggi senza effetto**: l'entrypoint ne ricava `GOVPAY_GDE_ENABLED` e `GOVPAY_GDE_BASE_URL`, che il batch non legge. L'invio al GDE si configura con il connettore `govpay_gde_api` di GovPay |
| `GOVPAY_NOTIFY_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch, se la tabella `GOVPAY_NOTIFY_DB_CHECK_TABLE` non esiste |
| `GOVPAY_NOTIFY_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_NOTIFY_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_NOTIFY_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_NOTIFY_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_NOTIFY_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_NOTIFY_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica della tabella e crea comunque lo schema |
| `GOVPAY_NOTIFY_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica della tabella |
| `GOVPAY_NOTIFY_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_NOTIFY_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_NOTIFY_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_NOTIFY_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_NOTIFY_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_NOTIFY_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate. Il log di avvio dell'entrypoint è in `/tmp/entrypoint_debug.log` dentro il container.

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/DOCKER.md](docker/DOCKER.md) e [docker/SCANSIONE.md](docker/SCANSIONE.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

Con il profilo `cron` non c'è server HTTP: l'esito si ricava dall'exit code del processo e dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`). Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia `rtNotifyJob` in modo asincrono (`202`, oppure `409` se è già in esecuzione); se `govpay.batch.rt-send.enabled=true` avvia anche `rtSendJob`, senza influire sulla risposta |
| `GET /api/batch/run?force=true` | Avvia `rtNotifyJob` anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente di `rtNotifyJob` |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione |
| `GET /api/batch/cache/clear` | Svuota la cache dei connettori e dei client verso l'Ente |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Elaborazione

### `rtNotifyJob`

Un solo step chunk-oriented (`rtNotifyStep`):

- **Reader** (`RtNotifyReader`): seleziona le rendicontazioni di flussi acquisiti negli ultimi `govpay.batch.finestra-temporale` giorni, collegate a un singolo versamento, senza pagamento (`id_pagamento` nullo), con `esegui_recupero_rt` falso e `notifica_inviata` falso.
- **Processor** (`RtNotifyProcessor`): risale all'applicazione della pendenza e al suo connettore di integrazione, verifica che la versione sia `REST_2` e invia la rendicontazione con `POST /rendicontazioni`.
- **Writer** (`RtNotifyWriter`): imposta `notifica_inviata` quando l'Ente risponde 2xx o 4xx. Con un 4xx la rendicontazione non viene ritentata; con errori di rete, 5xx o configurazione mancante resta da notificare e viene ripresa all'esecuzione successiva.

Ogni chiamata produce un evento nel GDE (categoria `INTERFACCIA`, ruolo `CLIENT`, componente `API_ENTE`) con richiesta, risposta ed esito. Le date restituite dall'Ente sono accettate con millisecondi da 1 a 9 cifre, con o senza fuso orario.

### `rtSendJob`

Un solo step chunk-oriented (`rtSendStep`): legge da `notifiche` le righe di tipo `RICEVUTA` in stato `DA_SPEDIRE` con `data_prossima_spedizione` passata, costruisce la ricevuta dalla `rpt` collegata e la invia con `PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}`. Lo stato di ogni notifica è aggiornato in una transazione propria, come descritto in [Spedizione delle ricevute](#spedizione-delle-ricevute-rtsendjob).

## Licenza

GovPay Notify Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).
