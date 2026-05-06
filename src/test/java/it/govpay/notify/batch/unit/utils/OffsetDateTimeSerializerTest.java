package it.govpay.notify.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.utils.OffsetDateTimeSerializer;

@DisplayName("OffsetDateTimeSerializer")
class OffsetDateTimeSerializerTest {

    @Test
    @DisplayName("serialize formats OffsetDateTime with default pattern")
    void serializeDefaultPattern() throws IOException {
        OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
        JsonGenerator generator = mock(JsonGenerator.class);
        OffsetDateTime input = OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 123_000_000, ZoneOffset.ofHours(1));

        serializer.serialize(input, generator, null);

        verify(generator).writeString("2025-03-12T10:15:30.123+01:00");
    }

    @Test
    @DisplayName("serialize uses custom pattern")
    void serializeCustomPattern() throws IOException {
        OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer(
                Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);
        JsonGenerator generator = mock(JsonGenerator.class);
        OffsetDateTime input = OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 123_000_000, ZoneOffset.ofHours(1));

        serializer.serialize(input, generator, null);

        verify(generator).writeString("2025-03-12T10:15:30.123");
    }

    @Test
    @DisplayName("serialize null OffsetDateTime writes null string")
    void serializeNullValue() throws IOException {
        OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
        JsonGenerator generator = mock(JsonGenerator.class);

        assertDoesNotThrow(() -> serializer.serialize(null, generator, null));
        verify(generator).writeString(isNull(String.class));
    }
}
