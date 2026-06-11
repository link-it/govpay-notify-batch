package it.govpay.notify.batch.rt.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Risultato della spedizione prodotto dal processor e consumato dal writer.
 * Contiene tutti i dati necessari ad applicare l'update di stato corretto.
 */
@Data
@Builder
public class RtSendResult {

    /** Id della notifica (chiave primaria su tabella {@code notifiche}). */
    private final Long notificaId;

    /** Esito della spedizione. */
    private final RtSendOutcome outcome;

    /** Descrizione/messaggio di errore (null per {@link RtSendOutcome#SUCCESS}). */
    private final String descrizione;

    /**
     * Valore aggiornato di {@code tentativi_spedizione} (= precedente + 1)
     * per ERROR/ABORT; null per SUCCESS.
     */
    private final Long tentativiSpedizione;
}
