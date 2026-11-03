package it.govpay.notify.batch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import it.govpay.pendenze.service.PosizioneDebitoriaService;

/**
 * Servizi di govpay-common-pendenze usati dal batch. Si registra solo
 * {@link PosizioneDebitoriaService}, per leggere la pendenza della ricevuta
 * ({@code trovaPendenzaCompletaPerId}): il pacchetto dei servizi della libreria non e'
 * scansionato.
 */
@Configuration
@Import(PosizioneDebitoriaService.class)
public class PendenzeServiceConfig {
}
