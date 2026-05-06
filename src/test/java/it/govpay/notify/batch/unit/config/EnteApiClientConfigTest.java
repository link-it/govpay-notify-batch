package it.govpay.notify.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.govpay.notify.batch.config.EnteApiClientConfig;

@DisplayName("EnteApiClientConfig")
class EnteApiClientConfigTest {

    private EnteApiClientConfig config;

    @BeforeEach
    void setUp() {
        config = new EnteApiClientConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");
    }

    @Test
    @DisplayName("createEnteObjectMapper returns a configured ObjectMapper")
    void createEnteObjectMapperConfigured() {
        ObjectMapper mapper = config.createEnteObjectMapper();

        assertNotNull(mapper);
        assertEquals(TimeZone.getTimeZone("Europe/Rome"), mapper.getSerializationConfig().getTimeZone());
        assertTrue(mapper.isEnabled(DeserializationFeature.READ_ENUMS_USING_TO_STRING));
        assertTrue(mapper.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING));
        assertTrue(mapper.isEnabled(SerializationFeature.WRITE_DATES_WITH_ZONE_ID));
        assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    @DisplayName("ObjectMapper deserializes OffsetDateTime with flexible milliseconds")
    void deserializeOffsetDateTime() throws Exception {
        ObjectMapper mapper = config.createEnteObjectMapper();
        String json = "\"2025-03-12T10:15:30.1+01:00\"";

        OffsetDateTime parsed = mapper.readValue(json, OffsetDateTime.class);

        assertEquals(2025, parsed.getYear());
        assertEquals(ZoneOffset.ofHours(1), parsed.getOffset());
    }

    @Test
    @DisplayName("ObjectMapper deserializes LocalDate from full datetime")
    void deserializeLocalDateFromDatetime() throws Exception {
        ObjectMapper mapper = config.createEnteObjectMapper();
        String json = "\"2025-03-12T00:00:00.000000+02:00\"";

        LocalDate parsed = mapper.readValue(json, LocalDate.class);

        assertEquals(LocalDate.of(2025, 3, 12), parsed);
    }

    @Test
    @DisplayName("ObjectMapper serializes OffsetDateTime with 3-digit milliseconds")
    void serializeOffsetDateTime() throws Exception {
        ObjectMapper mapper = config.createEnteObjectMapper();
        OffsetDateTime input = OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 123_000_000, ZoneOffset.ofHours(1));

        String json = mapper.writeValueAsString(input);

        assertEquals("\"2025-03-12T10:15:30.123\"", json);
    }
}
