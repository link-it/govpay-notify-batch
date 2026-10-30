package it.govpay.notify.batch.rt.service;

/**
 * Pendenza scartata dalla migrazione al modello v3 (tabella {@code migrazione_v3_scarti}):
 * senza opzione di pagamento non c'e' posizione debitoria da cui costruire la
 * {@code PendenzaPagata}. Errore non bloccante: la notifica riparte dopo la bonifica.
 */
public class PendenzaNonMigrataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PendenzaNonMigrataException(Long idPendenza) {
        super("Pendenza non migrata al modello v3 (id " + idPendenza + ")");
    }
}
