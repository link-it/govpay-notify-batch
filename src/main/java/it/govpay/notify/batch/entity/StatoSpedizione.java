package it.govpay.notify.batch.entity;

/**
 * Stato di spedizione di una notifica verso l'Ente.
 * Allineato a {@code it.govpay.model.Notifica.StatoSpedizione} del monolite GovPay.
 */
public enum StatoSpedizione {
    DA_SPEDIRE,
    SPEDITO,
    ANNULLATA
}
