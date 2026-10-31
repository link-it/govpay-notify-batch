package it.govpay.notify.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import it.govpay.pendenze.config.GeneratoreIuvAutoConfiguration;

/**
 * Classe principale di GovPay Notify Batch.
 * <p>
 * Estende {@link SpringBootServletInitializer} per il deploy del war (profilo
 * Maven {@code war}) su application server; con il fat jar parte da {@link #main}.
 * <p>
 * Le pendenze si leggono con entity e repository di govpay-common-pendenze. Il generatore
 * di IUV della libreria e' escluso: il batch non genera IUV.
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.notify.batch", "it.govpay.common.client"},
		exclude = GeneratoreIuvAutoConfiguration.class)
@EntityScan(basePackages = {"it.govpay.notify.batch", "it.govpay.common.client", "it.govpay.common.entity",
		"it.govpay.pendenze.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.notify.batch", "it.govpay.pendenze.repository"})
@EnableScheduling
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
