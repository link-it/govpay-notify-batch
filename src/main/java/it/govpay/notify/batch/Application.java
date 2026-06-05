package it.govpay.notify.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay RT Batch
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.notify.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.notify.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.notify.batch"})
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
