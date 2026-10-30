package it.govpay.notify.batch.rt.dto;

import java.util.List;

import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import lombok.Builder;
import lombok.Data;

/**
 * Notifica, RPT e pendenza pagata passate dal reader al processor del job di spedizione RT.
 * Mantenere le entity insieme evita re-lookup nel processor e nel writer.
 */
@Data
@Builder
public class NotificaContext {

    private final Notifica notifica;
    private final Rpt rpt;

    /** Pendenza della RPT letta con govpay-common-pendenze, {@code null} se non trovata. */
    private final Pendenza pendenza;

    /** Riscossioni della RPT, vuota se non ce ne sono. */
    @Builder.Default
    private final List<Pagamento> pagamenti = List.of();
}
