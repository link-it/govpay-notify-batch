package it.govpay.notify.batch.config;

import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import it.govpay.common.repository.IbanAccreditoRepository;
import it.govpay.common.repository.TipoVersamentoRepository;
import it.govpay.common.repository.TributoRepository;
import it.govpay.common.repository.UnitaOperativaRepository;

/**
 * Repository di anagrafica di {@code govpay-common} usati da
 * {@link it.govpay.notify.batch.rt.service.PendenzaPagataMapper}.
 * {@code GovPayClientAutoConfiguration} li esclude dai repository che registra, quindi
 * vanno abilitati qui. Il filtro include solo questi: gli altri repository del pacchetto
 * (domini, applicazioni, ...) sono gia' registrati dall'auto-configurazione.
 */
@Configuration
@EnableJpaRepositories(
        basePackageClasses = UnitaOperativaRepository.class,
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                UnitaOperativaRepository.class,
                TipoVersamentoRepository.class,
                TributoRepository.class,
                IbanAccreditoRepository.class }))
public class AnagraficheRepositoryConfig {
}
