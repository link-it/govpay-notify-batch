<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay Notify Batch

[![GitHub](https://img.shields.io/badge/GitHub-link--it%2Fgovpay--notify--batch-blue?logo=github)](https://github.com/link-it/govpay-notify-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Batch Spring Boot di [GovPay](https://github.com/link-it/govpay) che notifica alle applicazioni dell'Ente Creditore le rendicontazioni senza pagamento associato e, se abilitato, spedisce le ricevute di pagamento (RT), tramite l'API di integrazione dell'Ente.

## Funzionalità

- `rtNotifyJob`: invia all'applicazione proprietaria della pendenza le rendicontazioni acquisite da pagoPA che non hanno un pagamento in GovPay
- `rtSendJob` (disattivo di default): spedisce le ricevute di pagamento in coda in GovPay, con nuovi tentativi a intervalli crescenti
- Scheduler interno (default) oppure esecuzione singola da pianificare all'esterno (`GOVPAY_NOTIFY_BATCH_USA_CRON=true`)
- Coordinamento tra più nodi tramite i metadati Spring Batch sul database
- Database PostgreSQL, MySQL/MariaDB e Oracle; driver JDBC da montare in `/opt/jdbc-drivers`
- Eventi nel Giornale degli Eventi, configurato con il connettore `govpay_gde_api` di GovPay
- Health check, metriche Prometheus e API REST per l'avvio manuale sulla porta `10001`

## Avvio

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

L'elenco completo delle variabili d'ambiente è nel [README, sezione Esecuzione con Docker](https://github.com/link-it/govpay-notify-batch/blob/main/README.md#esecuzione-con-docker).

## Tag

- `latest`: ultima release
- `<versione>`: una release specifica, come da [release GitHub](https://github.com/link-it/govpay-notify-batch/releases)

Le immagini di sviluppo, costruite a ogni push su `main`, sono pubblicate come `linkitaly/govpay-notify-batch-dev`. Storico delle modifiche nel [ChangeLog](https://github.com/link-it/govpay-notify-batch/blob/main/ChangeLog).

## Documentazione

- [README](https://github.com/link-it/govpay-notify-batch/blob/main/README.md): configurazione, esecuzione, monitoraggio, API REST
- [Immagine Docker](https://github.com/link-it/govpay-notify-batch/blob/main/docker/DOCKER.md): contenuto, costruzione, modalità di esecuzione
- [Scansione dell'immagine](https://github.com/link-it/govpay-notify-batch/blob/main/docker/SCANSIONE.md)
- [Dockerfile](https://github.com/link-it/govpay-notify-batch/blob/main/docker/govpay-notify/Dockerfile.github)

## Licenza

GovPay Notify Batch è rilasciato con licenza [GPL v3](https://www.gnu.org/licenses/gpl-3.0).

---

Sviluppato da [Link.it s.r.l.](https://www.link.it)
