package it.govpay.notify.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for batch processing
 */
@Configuration
@ConfigurationProperties(prefix = "govpay.batch")
@Data
public class BatchProperties {

    /**
     * Chunk size for Notify
     */
    private int notifyChunkSize = 1;

    /**
     * Enable/disable automatic scheduling
     */
    private boolean enabled = true;

}
