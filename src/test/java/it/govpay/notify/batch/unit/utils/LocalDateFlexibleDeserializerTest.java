package it.govpay.notify.batch.unit.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import it.govpay.notify.batch.utils.LocalDateFlexibleDeserializer;

@DisplayName("LocalDateFlexibleDeserializer")
class LocalDateFlexibleDeserializerTest {

    private final LocalDateFlexibleDeserializer deserializer = new LocalDateFlexibleDeserializer();

    @Test
    @DisplayName("parse standard ISO LocalDate")
    void parseStandardIsoDate() {
        assertEquals(LocalDate.of(2025, 3, 12), deserializer.parseLocalDate("2025-03-12"));
    }

    @Test
    @DisplayName("parse OffsetDateTime string and extract date")
    void parseOffsetDateTimeAndExtractDate() {
        assertEquals(LocalDate.of(2025, 3, 12),
                deserializer.parseLocalDate("2025-03-12T00:00:00.000000+02:00"));
    }

    @Test
    @DisplayName("parse LocalDateTime string and extract date")
    void parseLocalDateTimeAndExtractDate() {
        assertEquals(LocalDate.of(2025, 3, 12),
                deserializer.parseLocalDate("2025-03-12T10:15:30"));
    }

    @Test
    @DisplayName("return null for null/blank input")
    void returnNullForBlankInput() {
        assertNull(deserializer.parseLocalDate(null));
        assertNull(deserializer.parseLocalDate(""));
        assertNull(deserializer.parseLocalDate("   "));
    }

    @Test
    @DisplayName("trim whitespace before parsing")
    void trimWhitespace() {
        assertEquals(LocalDate.of(2025, 3, 12), deserializer.parseLocalDate("  2025-03-12  "));
    }

    @Test
    @DisplayName("throw DateTimeParseException for unparseable input")
    void throwForUnparseable() {
        assertThrows(DateTimeParseException.class, () -> deserializer.parseLocalDate("not-a-date"));
    }

    @Test
    @DisplayName("deserialize parses string token to LocalDate")
    void deserializeStringToken() throws IOException {
        JsonParser parser = mock(JsonParser.class);
        when(parser.getCurrentToken()).thenReturn(JsonToken.VALUE_STRING);
        when(parser.getText()).thenReturn("2025-03-12");

        assertEquals(LocalDate.of(2025, 3, 12), deserializer.deserialize(parser, null));
    }

    @Test
    @DisplayName("deserialize returns null for non-string token")
    void deserializeNonStringToken() throws IOException {
        JsonParser parser = mock(JsonParser.class);
        when(parser.getCurrentToken()).thenReturn(JsonToken.VALUE_NULL);

        assertNull(deserializer.deserialize(parser, null));
    }

    @Test
    @DisplayName("deserialize wraps parse exception as IOException")
    void deserializeWrapsParseException() throws IOException {
        JsonParser parser = mock(JsonParser.class);
        when(parser.getCurrentToken()).thenReturn(JsonToken.VALUE_STRING);
        when(parser.getText()).thenReturn("not-a-date");

        assertThrows(IOException.class, () -> deserializer.deserialize(parser, null));
    }
}
