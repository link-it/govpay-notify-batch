# GovPay Notify Batch - Immagine Docker

Immagine del batch che notifica alle applicazioni dell'Ente Creditore le rendicontazioni senza pagamento associato (`rtNotifyJob`) e, se abilitato, spedisce le ricevute di pagamento (`rtSendJob`). Cosa fa il batch, come si configura e l'elenco completo delle variabili d'ambiente sono nel [README](../README.md#esecuzione-con-docker): qui solo ciò che riguarda l'immagine.

## Contenuto

| Percorso | Contenuto |
|----------|-----------|
| `/opt/govpay-notify/govpay-notify-batch.jar` | Fat jar del batch |
| `/opt/jdbc-drivers` | Driver JDBC, da montare: non sono inclusi nell'immagine |
| `/opt/sql` | Script SQL di `sql.zip`: schema Spring Batch per database (`spring-batch/`) e svecchiamento dei metadati (`cleanup/`) |
| `/opt/hsqldb-<versione>` | SqlTool di HSQLDB, usato per creare lo schema Spring Batch all'avvio |
| `/var/log/govpay-notify/govpay-notify.log` | File di log dell'applicazione |
| `/tmp/entrypoint_debug.log` | Traccia dell'entrypoint, utile se il container non parte |

Il processo gira con l'utente `govpay`; l'entrypoint è `/usr/local/bin/entrypoint.sh`.

## Costruzione

```bash
cd docker
./build_image.sh                       # ultima release pubblicata su GitHub
./build_image.sh -v <versione>         # una release specifica
./build_image.sh -l ../target/govpay-notify-batch.jar -t govpay-notify-batch:locale   # jar compilato in locale
```

Con una release, jar e `sql.zip` sono scaricati da GitHub e verificati con lo sha256 letto dalle API delle release (`-s` e `-q` per indicarlo a mano). Con `-l` si usa `Dockerfile.daFile` e gli script SQL vengono da `../target/sql.zip`, prodotto da `mvn clean install -Pjar,dist`: senza, l'immagine non ha gli script per creare lo schema. `./build_image.sh -h` elenca tutte le opzioni.

## Modalità di esecuzione

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (`GOVPAY_NOTIFY_BATCH_USA_CRON` non impostata o falsa) | Il container resta attivo, esegue `rtNotifyJob` a intervalli e, se abilitato, `rtSendJob`; espone API REST e actuator sulla porta `10001` |
| Esecuzione singola | `GOVPAY_NOTIFY_BATCH_USA_CRON=true` (anche `si`, `yes`, `1`) | Profilo `cron`: esegue `rtNotifyJob` una volta e termina, senza server HTTP. La pianificazione è esterna (cron di sistema, CronJob Kubernetes); `rtSendJob` non viene eseguito |

Con lo scheduler interno l'intervallo di `rtNotifyJob` si indica in minuti con `GOVPAY_NOTIFY_BATCH_INTERVALLO_CRON` (default `120`), che l'entrypoint converte nella proprietà `scheduler.rtNotifyJob.fixedDelayString`. Se è impostata direttamente `SCHEDULER_RTNOTIFYJOB_FIXEDDELAYSTRING` (in millisecondi), vale quella e `GOVPAY_NOTIFY_BATCH_INTERVALLO_CRON` viene ignorata. Il valore usato è scritto nel log di avvio:

```
[INFO] ... - Porta HTTP: 10001, Intervallo scheduler: 120 minuti (7200000ms)
```

In esecuzione singola l'exit code è `0` anche quando il job fallisce: l'esito va letto nei log.

## Schema Spring Batch

Con `GOVPAY_NOTIFY_POP_DB_SKIP=FALSE` l'entrypoint attende il database, controlla se esiste la tabella `GOVPAY_NOTIFY_DB_CHECK_TABLE` (default `batch_job_execution_context`) e, se manca, crea le tabelle dei metadati Spring Batch con lo script `/opt/sql/spring-batch/schema-<database>.sql`. Le tabelle di GovPay non vengono create: sono degli script di installazione di GovPay.

## Giornale degli Eventi

L'invio degli eventi al GDE non si configura con variabili del container ma con il connettore `govpay_gde_api` di GovPay (tabella `connettori`). La variabile `GOVPAY_NOTIFY_GDE_URL`, presente in versioni precedenti di questa documentazione, non ha effetto: se è impostata l'entrypoint lo segnala con un avviso nel log.

## Scansione delle vulnerabilità

Le immagini pubblicate dalla pipeline sono scansionate prima della pubblicazione: vedi [SCANSIONE.md](SCANSIONE.md).
