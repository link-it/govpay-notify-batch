# Esporre la versione dell'API EC sul modello `Connettore`

## Contesto

In `govpay-notify-batch` stiamo aggiungendo un nuovo job batch per la
spedizione delle notifiche delle **ricevute di pagamento** (RT) verso le API
di integrazione dell'Ente Creditore. L'API EC esiste in due versioni
(`REST_1` -> `POST /pagamenti/{codDominio}/{iuv}` e
`REST_2` -> `PUT /ricevute/{idDominio}/{iuv}/{idRicevuta}`) e il client da
usare va scelto a runtime in funzione della versione configurata sul
connettore di integrazione dell'applicazione.

Nel monolite GovPay la dispatch e' centralizzata in
`it.govpay.core.utils.client.NotificaClient#invoke()` e si basa su
`applicazione.getConnettoreIntegrazione().getVersione()`, dove
`Connettore extends Versionabile` e quindi espone direttamente l'enum
`Versione` (`GP_REST_01`, `GP_REST_02`, ...).

## Problema

In `govpay-common` 2.0.0-SNAPSHOT il modello runtime
`it.govpay.common.client.model.Connettore` **non** espone la versione
dell'API: la proprieta' esiste solo come riga key/value nella tabella
`connettori` (`cod_proprieta = 'VERSIONE'`, `valore = 'REST_1' | 'REST_2'`,
costante `Connettore.P_VERSIONE` nel monolite). I consumer
(`govpay-notify-batch`, gli altri batch satellite) devono quindi:

1. chiamare `ConnettoreService.getConnettoreAsMap(codConnettore)`,
2. estrarre la stringa `"VERSIONE"`,
3. fare il parse a mano in un enum locale.

Questo:

- duplica logica di parsing in ogni consumer,
- non e' coerente con il monolite (che e' la fonte autorevole dello schema),
- rende fragile l'evoluzione (se domani la stringa cambia da `REST_2` a `REST_3`,
  vanno aggiornati tutti i consumer).

## Proposta

Allineare il modello `Connettore` di `govpay-common` al pattern del monolite
esponendo la versione come campo tipizzato del model runtime.

### Modifiche richieste

1. **Aggiungere un enum** `it.govpay.common.entity.VersioneApi` (oppure
   riusare un enum gia' presente in govpay-common, se esiste un equivalente).
   Valori minimi: `REST_1`, `REST_2`. Suggerito `fromValue(String)` /
   `getValue()` per la conversione dalla rappresentazione DB.

2. **Aggiungere il campo `versione`** al model
   `it.govpay.common.client.model.Connettore`:

   ```java
   private VersioneApi versione;
   public VersioneApi getVersione() { return this.versione; }
   public void setVersione(VersioneApi versione) { this.versione = versione; }
   ```

3. **Popolare il campo** durante la conversione da entity a model nel
   service / factory che oggi compone `Connettore` dalla mappa di proprieta'
   (`ConnettoreService` / `RestTemplateFactory`): quando si incontra la
   proprieta' `VERSIONE`, valorizzare il campo strong-typed.

4. **Default e tolleranza al null**: se la proprieta' non e' configurata sul
   connettore, lasciare `versione = null`. La scelta del default applicativo
   resta in capo al consumer (es. `govpay-notify-batch` puo' decidere di
   trattare `null` come `REST_2`).

5. **Backwards compatibility**: la mappa restituita da
   `ConnettoreService.getConnettoreAsMap(codConnettore)` deve continuare a
   contenere anche la chiave `VERSIONE` come oggi, per non rompere i consumer
   che la leggono in quel modo.

### Test

- Unit test sul service/factory che compone `Connettore`: dato un set di
  righe key/value che include `VERSIONE=REST_2`, verificare che
  `getVersione() == VersioneApi.REST_2`.
- Caso `VERSIONE` assente -> `getVersione() == null`.
- Caso `VERSIONE` con valore non riconosciuto -> definire policy (eccezione
  in fase di load, oppure `null` + warning log). Suggerito: eccezione, per
  evitare invii silenziosi sulla versione "sbagliata".

## Versione target

`govpay-common` 2.0.1-SNAPSHOT (o piu' in generale: prima della release
stabile 2.0.0, se ancora aperta).

## Riferimenti

- `it.govpay.model.Versionabile` (monolite GovPay): definizione enum
  `Versione`, parent di `Connettore`.
- `it.govpay.bd.model.converter.ConnettoreConverter` (monolite GovPay):
  read/write della proprieta' `Connettore.P_VERSIONE` su tabella `connettori`.
- `it.govpay.core.utils.client.NotificaClient#invoke()`: dispatch v1/v2
  basato su `connettoreIntegrazione.getVersione()`.
