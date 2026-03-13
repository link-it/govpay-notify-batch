package it.govpay.notify.batch.unit.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gde.client.beans.RuoloEvento;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.mapper.EventoRtMapper;

@DisplayName("EventoRtMapper")
class EventoRtMapperTest {

    private EventoRtMapper mapper;
    private RtNotifyContext rtInfo;
    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    private static final String CLUSTER_ID = "test-cluster";
    private static final String TAX_CODE = "12345678901";
    private static final String IUV = "01234567890123456";
    private static final String IUR = "IUR123456";
    private static final String TIPO_EVENTO = "NOTIFY_RND";
    private static final String TRANSACTION_ID = "txn-123";

    @BeforeEach
    void setUp() {
        mapper = new EventoRtMapper();
        ReflectionTestUtils.setField(mapper, "clusterId", CLUSTER_ID);

        rtInfo = RtNotifyContext.builder()
                .rtId(1L)
                .taxCode(TAX_CODE)
                .iuv(IUV)
                .iur(IUR)
                .build();

        dataStart = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2024, 1, 15, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    @Nested
    @DisplayName("createEvento")
    class CreateEventoTest {

        @Test
        @DisplayName("should create base event with all fields populated")
        void shouldCreateBaseEventWithAllFields() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertEquals(TAX_CODE, evento.getIdDominio());
            assertEquals(IUV, evento.getIuv());
            assertEquals(IUR, evento.getCcp());
            assertEquals(CategoriaEvento.INTERFACCIA, evento.getCategoriaEvento());
            assertEquals(CLUSTER_ID, evento.getClusterId());
            assertEquals(dataStart, evento.getDataEvento());
            assertEquals(5000L, evento.getDurataEvento()); // 5 seconds = 5000ms
            assertEquals(RuoloEvento.CLIENT, evento.getRuolo());
            assertEquals(ComponenteEvento.API_ENTE, evento.getComponente());
            assertEquals(TIPO_EVENTO, evento.getTipoEvento());
            assertEquals(TRANSACTION_ID, evento.getTransactionId());
        }

        @Test
        @DisplayName("should populate datiPagoPA with taxCode")
        void shouldPopulateDatiPagoPA() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento.getDatiPagoPA());
            assertEquals(TAX_CODE, evento.getDatiPagoPA().getIdDominio());
        }

        @Test
        @DisplayName("should handle null rtInfo")
        void shouldHandleNullRtInfo() {
            NuovoEvento evento = mapper.createEvento(null, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertNull(evento.getIdDominio());
            assertNull(evento.getIuv());
            assertNull(evento.getCcp());
            assertNull(evento.getDatiPagoPA());
        }
    }

    @Nested
    @DisplayName("createEventoOk")
    class CreateEventoOkTest {

        @Test
        @DisplayName("should create event with OK esito")
        void shouldCreateEventWithOkEsito() {
            NuovoEvento evento = mapper.createEventoOk(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            assertNotNull(evento);
            assertEquals(EsitoEvento.OK, evento.getEsito());
        }
    }

    @Nested
    @DisplayName("createEventoKo")
    class CreateEventoKoTest {

        @Test
        @DisplayName("should set KO esito for 4xx client errors")
        void shouldSetKoEsitoFor4xxErrors() {
            HttpClientErrorException exception = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "error".getBytes(), null);

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.KO, evento.getEsito());
            assertEquals("400", evento.getSottotipoEsito());
        }

        @Test
        @DisplayName("should set FAIL esito for 5xx server errors")
        void shouldSetFailEsitoFor5xxErrors() {
            HttpServerErrorException exception = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, "error".getBytes(), null);

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.FAIL, evento.getEsito());
            assertEquals("500", evento.getSottotipoEsito());
        }

        @Test
        @DisplayName("should set FAIL esito for non-HTTP exceptions")
        void shouldSetFailEsitoForNonHttpExceptions() {
            RestClientException exception = new RestClientException("Connection refused");

            NuovoEvento evento = mapper.createEventoKo(rtInfo, TIPO_EVENTO, TRANSACTION_ID,
                    dataStart, dataEnd, null, exception);

            assertEquals(EsitoEvento.FAIL, evento.getEsito());
            assertEquals("500", evento.getSottotipoEsito());
            assertEquals("Connection refused", evento.getDettaglioEsito());
        }
    }

    @Nested
    @DisplayName("setParametriRichiesta")
    class SetParametriRichiestaTest {

        @Test
        @DisplayName("should set request parameters including json body")
        void shouldSetRequestParameters() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            Header header = new Header();
            header.setNome("Content-Type");
            header.setValore("application/json");
            String jsonBody = "{\"idDominio\":\"12345678901\"}";

            mapper.setParametriRichiesta(evento, "http://api.test/rendicontazioni", "POST",
                    Collections.singletonList(header), jsonBody);

            assertNotNull(evento.getParametriRichiesta());
            assertEquals("http://api.test/rendicontazioni", evento.getParametriRichiesta().getUrl());
            assertEquals("POST", evento.getParametriRichiesta().getMethod());
            assertEquals(1, evento.getParametriRichiesta().getHeaders().size());
            assertEquals(jsonBody, evento.getParametriRichiesta().getPayload());
        }
    }

    @Nested
    @DisplayName("setParametriRisposta")
    class SetParametriRispostaTest {

        @Test
        @DisplayName("should set response status from ResponseEntity")
        void shouldSetResponseStatusFromResponseEntity() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            mapper.setParametriRisposta(evento, dataEnd, ResponseEntity.ok("body"), null);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(200), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("should set response status from HttpStatusCodeException")
        void shouldSetResponseStatusFromException() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
            HttpClientErrorException exception = HttpClientErrorException.create(
                    HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);

            mapper.setParametriRisposta(evento, dataEnd, null, exception);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(404), evento.getParametriRisposta().getStatus());
        }

        @Test
        @DisplayName("should set status 500 when both response and exception are null")
        void shouldSetStatus500WhenBothNull() {
            NuovoEvento evento = mapper.createEvento(rtInfo, TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

            mapper.setParametriRisposta(evento, dataEnd, null, null);

            assertNotNull(evento.getParametriRisposta());
            assertEquals(BigDecimal.valueOf(500), evento.getParametriRisposta().getStatus());
        }
    }
}
