package it.govpay.notify.batch.unit.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.ec.client.beans.EsitoRpp;
import it.govpay.ec.client.beans.Ricevuta;
import it.govpay.ec.client.beans.RicevutaRt;
import it.govpay.notify.batch.entity.Rpt;
import it.govpay.notify.batch.rt.service.RicevutaV2Mapper;

@DisplayName("RicevutaV2Mapper")
class RicevutaV2MapperTest {

    private RicevutaV2Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RicevutaV2Mapper(ZoneId.of("Europe/Rome"));
    }

    private Rpt baseRpt() {
        return Rpt.builder()
                .id(1L)
                .codDominio("12345678901")
                .iuv("01234567890123456")
                .ccp("CCP-TEST")
                .codSessione("sess-1")
                .codSessionePortale("portale-1")
                .codCarrello("carrello-1")
                .modelloPagamento("1")
                .dataMsgRicevuta(LocalDateTime.of(2026, 6, 1, 10, 30))
                .codEsitoPagamento(0)
                .importoTotalePagato(new BigDecimal("12.34"))
                .xmlRt("<rt/>".getBytes())
                .tipoIdentificativoAttestante("B")
                .identificativoAttestante("BNLIITRR")
                .denominazioneAttestante("BNL")
                .codPsp("PSP-1")
                .codCanale("CANALE-1")
                .idVersamento(99L)
                .versione("SANP_321_V2")
                .build();
    }

    @Test
    @DisplayName("mappa i campi base, l'istituto attestante e il body RT (CT_RECEIPT)")
    void mapsAllFields() {
        Ricevuta r = mapper.toRicevuta(baseRpt());

        assertNotNull(r.getDominio());
        assertEquals("12345678901", r.getDominio().getIdDominio());
        assertEquals("01234567890123456", r.getIuv());
        assertEquals("CCP-TEST", r.getIdRicevuta());
        assertEquals(new BigDecimal("12.34"), r.getImporto());
        assertEquals(EsitoRpp.ESEGUITO, r.getEsito());
        assertNotNull(r.getData());
        assertEquals(r.getData(), r.getDataPagamento());

        assertNotNull(r.getIstitutoAttestante());
        assertEquals("BNLIITRR", r.getIstitutoAttestante().getIdPSP());
        assertEquals("BNL", r.getIstitutoAttestante().getDenominazione());
        assertEquals("CANALE-1", r.getIstitutoAttestante().getIdCanale());

        assertNotNull(r.getRt());
        assertEquals(RicevutaRt.TipoEnum.CT_RECEIPT, r.getRt().getTipo());
        assertNotNull(r.getRt().getXml());
    }

    @Test
    @DisplayName("SANP_230 produce un RT con tipo CT_RICEVUTA_TELEMATICA")
    void sanp230ToCtRicevutaTelematica() {
        Rpt rpt = baseRpt();
        rpt.setVersione("SANP_230");
        Ricevuta r = mapper.toRicevuta(rpt);
        assertEquals(RicevutaRt.TipoEnum.CT_RICEVUTA_TELEMATICA, r.getRt().getTipo());
    }

    @Test
    @DisplayName("xml RT assente -> body RT non valorizzato")
    void noXmlRtNoRtBody() {
        Rpt rpt = baseRpt();
        rpt.setXmlRt(null);
        Ricevuta r = mapper.toRicevuta(rpt);
        assertNull(r.getRt());
    }

    @Test
    @DisplayName("identificativo attestante assente -> istituto attestante non valorizzato")
    void noAttestanteNoIstituto() {
        Rpt rpt = baseRpt();
        rpt.setIdentificativoAttestante(null);
        Ricevuta r = mapper.toRicevuta(rpt);
        assertNull(r.getIstitutoAttestante());
    }

    @Test
    @DisplayName("data messaggio ricevuta assente -> data e dataPagamento non valorizzate")
    void dataMsgRicevutaAssente() {
        Rpt rpt = baseRpt();
        rpt.setDataMsgRicevuta(null);

        Ricevuta r = mapper.toRicevuta(rpt);

        assertNull(r.getData());
        assertNull(r.getDataPagamento());
    }

    @Test
    @DisplayName("codice esito pagamento assente -> esito non valorizzato")
    void codEsitoPagamentoAssente() {
        Rpt rpt = baseRpt();
        rpt.setCodEsitoPagamento(null);

        Ricevuta r = mapper.toRicevuta(rpt);

        assertNull(r.getEsito());
    }

    @Test
    @DisplayName("codice esito non riconosciuto solleva IllegalArgumentException")
    void unknownEsito() {
        Rpt rpt = baseRpt();
        rpt.setCodEsitoPagamento(99);
        assertThrows(IllegalArgumentException.class, () -> mapper.toRicevuta(rpt));
    }

    @Test
    @DisplayName("esiti 0..4 mappati 1-a-1 su EsitoRpp")
    void allKnownEsiti() {
        assertEquals(EsitoRpp.ESEGUITO, mapEsito(0));
        assertEquals(EsitoRpp.NON_ESEGUITO, mapEsito(1));
        assertEquals(EsitoRpp.ESEGUITO_PARZIALE, mapEsito(2));
        assertEquals(EsitoRpp.DECORRENZA, mapEsito(3));
        assertEquals(EsitoRpp.DECORRENZA_PARZIALE, mapEsito(4));
    }

    private EsitoRpp mapEsito(int code) {
        Rpt rpt = baseRpt();
        rpt.setCodEsitoPagamento(code);
        return mapper.toRicevuta(rpt).getEsito();
    }
}
