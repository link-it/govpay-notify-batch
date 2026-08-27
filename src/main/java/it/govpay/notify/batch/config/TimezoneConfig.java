package it.govpay.notify.batch.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurazione del timezone dell'applicazione.
 * Imposta il timezone di default della JVM leggendo dalla proprieta' spring.jackson.time-zone.
 */
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimezoneConfig {

    @Getter
    @Value("${spring.jackson.time-zone:Europe/Rome}")
    private String timezone;

    @PostConstruct
    public void init() {
        TimeZone timeZone = TimeZone.getTimeZone(timezone);
        TimeZone.setDefault(timeZone);
        log.info("Timezone di default impostato a: {}", timeZone.getID());
    }

    @Bean
    public ZoneId applicationZoneId() {
        return ZoneId.of(timezone);
    }

    /**
     * Orologio applicativo agganciato al timezone configurato: e' la sorgente
     * unica del "tempo corrente" per reader, processor, writer e listener del
     * batch. Va usato al posto delle {@code now()} senza argomenti, che
     * dipendono dal default della JVM (implicito e non sostituibile nei test).
     */
    @Bean
    public Clock applicationClock(ZoneId applicationZoneId) {
        return Clock.system(applicationZoneId);
    }
}
