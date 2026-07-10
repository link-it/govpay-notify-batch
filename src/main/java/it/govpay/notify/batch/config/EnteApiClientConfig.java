package it.govpay.notify.batch.config;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.notify.batch.Costanti;
import it.govpay.notify.batch.utils.LocalDateFlexibleDeserializer;
import it.govpay.notify.batch.utils.OffsetDateTimeDeserializer;
import it.govpay.notify.batch.utils.OffsetDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for RT API client.
 * Provides the custom JsonMapper (Jackson 3) for pagoPA date handling,
 * used by EnteApiService when creating per-domain RestTemplate instances.
 * <p>
 * Migrato a Jackson 3 (tools.jackson): builder-style JsonMapper.builder(),
 * JavaTimeModule non piu' necessario (java.time e' built-in), SerializationInclusion
 * NON_NULL configurato esplicitamente per riprodurre il {@code @JsonInclude(NON_NULL)}
 * di default delle bean del client OpenAPI generato (annotazioni Jackson 2 ignorate
 * da Jackson 3).
 */
@Slf4j
@Configuration
public class EnteApiClientConfig {

    @Value("${spring.jackson.time-zone:Europe/Rome}")
    private String timezone;

    /**
     * Creates a custom JsonMapper for pagoPA API client with enhanced date handling security.
     * <p>
     * Configuration:
     * - Serialization: uses fixed format yyyy-MM-dd'T'HH:mm:ss.SSS
     * - Deserialization: accepts variable-length milliseconds (1-9 digits) for security
     * - Fallback: if timezone is missing, defaults to CET
     * - Dates: written as ISO-8601 strings (not timestamps) with zone ID
     * - Timezone: configured from spring.jackson.time-zone property
     * - Property inclusion: NON_NULL (i null non vengono serializzati)
     *
     * @return configured JsonMapper for pagoPA API
     */
    public JsonMapper createEnteObjectMapper() {
        SimpleModule dateModule = new SimpleModule()
                .addSerializer(OffsetDateTime.class,
                        new OffsetDateTimeSerializer(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS))
                .addDeserializer(OffsetDateTime.class,
                        new OffsetDateTimeDeserializer(Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX))
                .addDeserializer(LocalDate.class, new LocalDateFlexibleDeserializer());

        // In Jackson 3 diverse feature Jackson 2 sono state rimosse perche' assorbite dai default:
        // - WRITE_DATES_AS_TIMESTAMPS / WRITE_DATES_WITH_ZONE_ID: Jackson 3 usa sempre ISO-8601 stringhe.
        // - WRITE_ENUMS_USING_TO_STRING / READ_ENUMS_USING_TO_STRING: la serializzazione degli enum
        //   passa tramite le annotazioni @JsonValue / @JsonCreator delle bean generate (condivise
        //   con Jackson 2 via jackson-annotations).
        return JsonMapper.builder()
                .defaultTimeZone(TimeZone.getTimeZone(timezone))
                .defaultDateFormat(new SimpleDateFormat(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS))
                .addModule(dateModule)
                .changeDefaultPropertyInclusion(inc -> inc.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
