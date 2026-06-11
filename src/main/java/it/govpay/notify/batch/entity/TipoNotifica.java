package it.govpay.notify.batch.entity;

/**
 * Tipo di notifica inviata all'Ente.
 * Allineato a {@code it.govpay.model.Notifica.TipoNotifica} del monolite GovPay.
 */
public enum TipoNotifica {
    ATTIVAZIONE,
    RICEVUTA,
    ANNULLAMENTO,
    FALLIMENTO
}
