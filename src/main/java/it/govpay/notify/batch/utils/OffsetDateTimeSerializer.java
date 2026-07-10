package it.govpay.notify.batch.utils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import it.govpay.notify.batch.Costanti;

/**
 * Custom serializer for OffsetDateTime to ensure consistent date format in JSON output.
 * Uses configurable date pattern for serialization (default: yyyy-MM-dd'T'HH:mm:ss.SSSXXX).
 * <p>
 * Migrato a Jackson 3 (tools.jackson): SerializerProvider e' stato rinominato
 * SerializationContext, IOException e' stata sostituita da JacksonException.
 */
public class OffsetDateTimeSerializer extends StdScalarSerializer<OffsetDateTime> {

	private static final long serialVersionUID = 1L;

	private transient DateTimeFormatter formatter;

	/**
	 * Default constructor using standard timestamp format with timezone.
	 */
	public OffsetDateTimeSerializer() {
		this(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
	}

	/**
	 * Constructor with custom date format pattern.
	 *
	 * @param format the date format pattern to use for serialization
	 */
	public OffsetDateTimeSerializer(String format) {
		super(OffsetDateTime.class);
		this.formatter = DateTimeFormatter.ofPattern(format);
	}

	@Override
	public void serialize(OffsetDateTime dateTime, JsonGenerator jsonGenerator, SerializationContext context)
			throws JacksonException {
		String dateTimeAsString = dateTime != null ? this.formatter.format(dateTime) : null;
		jsonGenerator.writeString(dateTimeAsString);
	}
}
