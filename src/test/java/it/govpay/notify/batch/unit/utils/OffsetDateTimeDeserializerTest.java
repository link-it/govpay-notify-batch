package it.govpay.notify.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.utils.OffsetDateTimeDeserializer;

@DisplayName("OffsetDateTimeDeserializer")
class OffsetDateTimeDeserializerTest {

    private final OffsetDateTimeDeserializer deserializer = new OffsetDateTimeDeserializer();
    private final DateTimeFormatter primaryFormatter = DateTimeFormatter.ofPattern(
            Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX);

    @Test
    @DisplayName("parse with primary formatter (3-digit milliseconds)")
    void parseWithPrimaryFormatter() {
        OffsetDateTime parsed = deserializer.parseOffsetDateTime("2025-03-12T10:15:30.123+01:00", primaryFormatter);
        assertEquals(OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 123_000_000, ZoneOffset.ofHours(1)), parsed);
    }

    @Test
    @DisplayName("parse with flexible offset formatter when seconds are missing")
    void parseFlexibleOffsetNoSeconds() {
        OffsetDateTime parsed = deserializer.parseOffsetDateTime("2025-12-09T00:00+01:00", primaryFormatter);
        assertEquals(OffsetDateTime.of(2025, 12, 9, 0, 0, 0, 0, ZoneOffset.ofHours(1)), parsed);
    }

    @Test
    @DisplayName("parse with flexible offset formatter — variable nanoseconds (1 digit)")
    void parseFlexibleOffsetSingleDigitFraction() {
        OffsetDateTime parsed = deserializer.parseOffsetDateTime("2025-12-09T10:15:30.1+01:00", primaryFormatter);
        assertNotNull(parsed);
        assertEquals(100_000_000, parsed.getNano());
    }

    @Test
    @DisplayName("fallback to LocalDateTime parse and apply CET offset")
    void parseLocalDateTimeAppliesCetOffset() {
        OffsetDateTime parsed = deserializer.parseOffsetDateTime("2025-03-12T10:15:30", primaryFormatter);
        assertEquals(OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 0, ZoneOffset.ofHours(1)), parsed);
    }

    @Test
    @DisplayName("Z timezone is recognized")
    void parseZuluTimezone() {
        OffsetDateTime parsed = deserializer.parseOffsetDateTime("2025-03-12T10:15:30.000Z", primaryFormatter);
        assertEquals(OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 0, ZoneOffset.UTC), parsed);
    }

    @Test
    @DisplayName("return null for null/blank input")
    void returnNullForBlankInput() {
        assertNull(deserializer.parseOffsetDateTime(null, primaryFormatter));
        assertNull(deserializer.parseOffsetDateTime("", primaryFormatter));
        assertNull(deserializer.parseOffsetDateTime("   ", primaryFormatter));
    }

    @Test
    @DisplayName("throw DateTimeParseException for unparseable input")
    void throwForUnparseable() {
        assertThrows(DateTimeParseException.class,
                () -> deserializer.parseOffsetDateTime("not-a-date", primaryFormatter));
    }

    @Test
    @DisplayName("custom format pattern is used")
    void customFormatIsUsed() {
        OffsetDateTimeDeserializer custom = new OffsetDateTimeDeserializer("yyyy-MM-dd'T'HH:mm:ssXXX");
        DateTimeFormatter customFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        OffsetDateTime parsed = custom.parseOffsetDateTime("2025-03-12T10:15:30+01:00", customFmt);
        assertEquals(OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 0, ZoneOffset.ofHours(1)), parsed);
    }

    @Test
    @DisplayName("deserialize parses string token to OffsetDateTime")
    void deserializeStringToken() {
        JsonParser parser = mock(JsonParser.class);
        when(parser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
        when(parser.getString()).thenReturn("2025-03-12T10:15:30.123+01:00");

        OffsetDateTime parsed = deserializer.deserialize(parser, null);
        assertEquals(OffsetDateTime.of(2025, 3, 12, 10, 15, 30, 123_000_000, ZoneOffset.ofHours(1)), parsed);
    }

    @Test
    @DisplayName("deserialize returns null for non-string token")
    void deserializeNonStringToken() {
        JsonParser parser = mock(JsonParser.class);
        when(parser.currentToken()).thenReturn(JsonToken.VALUE_NULL);

        assertNull(deserializer.deserialize(parser, null));
    }

    @Test
    @DisplayName("deserialize propagates DateTimeParseException for unparseable input")
    void deserializePropagatesParseException() {
        JsonParser parser = mock(JsonParser.class);
        when(parser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
        when(parser.getString()).thenReturn("not-a-date");

        assertThrows(DateTimeParseException.class, () -> deserializer.deserialize(parser, null));
    }
}
