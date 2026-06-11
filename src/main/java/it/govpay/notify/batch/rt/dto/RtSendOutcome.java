package it.govpay.notify.batch.rt.dto;

/**
 * Esito della spedizione di una singola notifica RT.
 * Determina quale update di stato applicare nel writer
 * (cfr. {@code NotificheBD} del monolite).
 */
public enum RtSendOutcome {
    /** HTTP 2xx -> updateSpedito */
    SUCCESS,
    /** Errore non bloccante (HTTP non 2xx, timeout, ecc.) -> updateDaSpedire con backoff */
    ERROR,
    /** Connettore non configurato o versione non supportata -> updateAnnullata */
    ABORT
}
