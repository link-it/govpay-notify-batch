package it.govpay.notify.batch.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.notify.batch.config.TimezoneConfig;

@DisplayName("TimezoneConfig")
class TimezoneConfigTest {

    private TimeZone originalDefault;
    private TimezoneConfig config;

    @BeforeEach
    void setUp() {
        originalDefault = TimeZone.getDefault();
        config = new TimezoneConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    @DisplayName("init sets the JVM default timezone")
    void initSetsDefaultTimezone() {
        config.init();

        assertEquals("Europe/Rome", TimeZone.getDefault().getID());
    }

    @Test
    @DisplayName("applicationZoneId bean returns the configured ZoneId")
    void applicationZoneIdReturnsConfigured() {
        ZoneId zoneId = config.applicationZoneId();

        assertEquals(ZoneId.of("Europe/Rome"), zoneId);
    }

    @Test
    @DisplayName("getTimezone returns configured timezone")
    void getTimezoneReturnsConfigured() {
        assertEquals("Europe/Rome", config.getTimezone());
    }
}
