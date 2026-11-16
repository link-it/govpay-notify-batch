package it.govpay.notify.batch.rt.dto;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import lombok.Builder;
import lombok.Data;

/**
 * Coppia notifica + RPT passata dal reader al processor del job di spedizione RT.
 * Mantenere le due entity insieme evita re-lookup nel processor e nel writer.
 */
@Data
@Builder
public class NotificaContext {

    private final Notifica notifica;
    private final Rpt rpt;
}
