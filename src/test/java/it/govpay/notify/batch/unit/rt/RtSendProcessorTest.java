package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.entity.Applicazione;
import it.govpay.notify.batch.entity.Notifica;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.rt.dto.NotificaContext;
import it.govpay.notify.batch.rt.dto.RtSendOutcome;
import it.govpay.notify.batch.rt.dto.RtSendResult;
import it.govpay.notify.batch.rt.service.EnteRicevutaApiService;
import it.govpay.notify.batch.rt.tasklet.RtSendProcessor;

@DisplayName("RtSendProcessor")
class RtSendProcessorTest {

    private EnteRicevutaApiService service;
    private RtSendProcessor processor;
    private NotificaContext ctx;

    @BeforeEach
    void setUp() {
        service = mock(EnteRicevutaApiService.class);
        processor = new RtSendProcessor(service);

        Notifica notifica = Notifica.builder()
                .id(42L)
                .tentativiSpedizione(2L)
                .applicazione(Applicazione.builder().codApplicazione("APP1").build())
                .build();
        Rpt rpt = Rpt.builder().id(7L).codDominio("DOM").iuv("IUV").ccp("CCP").build();
        ctx = NotificaContext.builder().notifica(notifica).rpt(rpt).build();
    }

    @Test
    @DisplayName("HTTP 2xx -> outcome SUCCESS, no descrizione, no tentativi")
    void success() {
        when(service.sendRicevuta(any(), any())).thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        RtSendResult r = processor.process(ctx);

        assertNotNull(r);
        assertEquals(42L, r.getNotificaId());
        assertEquals(RtSendOutcome.SUCCESS, r.getOutcome());
        assertNull(r.getDescrizione());
        assertNull(r.getTentativiSpedizione());
    }

    @Test
    @DisplayName("RestClientException -> outcome ERROR, tentativi+1, descrizione troncata")
    void error() {
        when(service.sendRicevuta(any(), any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        RtSendResult r = processor.process(ctx);

        assertEquals(RtSendOutcome.ERROR, r.getOutcome());
        assertEquals(3L, r.getTentativiSpedizione());
        assertNotNull(r.getDescrizione());
    }

    @Test
    @DisplayName("UnsupportedOperationException (versione) -> outcome ABORT")
    void abortVersione() {
        when(service.sendRicevuta(any(), any()))
                .thenThrow(new UnsupportedOperationException("versione REST_1 non supportata"));

        RtSendResult r = processor.process(ctx);

        assertEquals(RtSendOutcome.ABORT, r.getOutcome());
        assertEquals(3L, r.getTentativiSpedizione());
    }

    @Test
    @DisplayName("IllegalStateException (connettore assente) -> outcome ABORT")
    void abortConnettore() {
        when(service.sendRicevuta(any(), any()))
                .thenThrow(new IllegalStateException("connettore non configurato"));

        RtSendResult r = processor.process(ctx);

        assertEquals(RtSendOutcome.ABORT, r.getOutcome());
    }

    @Test
    @DisplayName("descrizione oltre i 255 char di descrizione_stato -> troncata con ellissi")
    void descrizioneTroncataAlLimiteDellaColonna() {
        String messaggioLungo = "X".repeat(400);
        when(service.sendRicevuta(any(), any())).thenThrow(new RestClientException(messaggioLungo));

        RtSendResult r = processor.process(ctx);

        assertEquals(Costanti.NOTIFICHE_DESCRIZIONE_STATO_MAX_LEN, r.getDescrizione().length());
        assertTrue(r.getDescrizione().endsWith("..."));
    }

    @Test
    @DisplayName("descrizione entro il limite -> passata invariata")
    void descrizioneCortaNonToccata() {
        when(service.sendRicevuta(any(), any())).thenThrow(new RestClientException("HTTP 503 dall'ente"));

        RtSendResult r = processor.process(ctx);

        assertEquals("HTTP 503 dall'ente", r.getDescrizione());
    }

    @Test
    @DisplayName("eccezione senza messaggio -> descrizione null, nessun NPE nel troncamento")
    void descrizioneNullNonRompeIlTroncamento() {
        when(service.sendRicevuta(any(), any())).thenThrow(new IllegalStateException((String) null));

        RtSendResult r = processor.process(ctx);

        assertEquals(RtSendOutcome.ABORT, r.getOutcome());
        assertNull(r.getDescrizione());
    }

    @Test
    @DisplayName("tentativi a null sulla notifica viene trattato come 0 di base")
    void tentativiNull() {
        ctx.getNotifica().setTentativiSpedizione(null);
        when(service.sendRicevuta(any(), any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"));

        RtSendResult r = processor.process(ctx);

        assertEquals(1L, r.getTentativiSpedizione());
    }
}
