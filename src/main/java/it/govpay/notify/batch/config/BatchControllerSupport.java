package it.govpay.notify.batch.config;

import java.time.ZoneId;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.env.Environment;

import it.govpay.common.batch.runner.JobExecutionHelper;
import jakarta.persistence.EntityManager;

/**
 * Collaboratori infrastrutturali richiesti da
 * {@code AbstractBatchController} di {@code govpay-common}.
 * <p>
 * Sono raggruppati in un unico bean per non farli comparire uno per uno nella
 * firma dei controller concreti: la superclasse ne richiede sei e il costruttore
 * del controller supererebbe il limite di parametri, nascondendo fra
 * l'infrastruttura le dipendenze che sono davvero specifiche del batch (i job,
 * i service, le feature flag).
 *
 * @param jobExecutionHelper      helper multi-nodo di govpay-common
 * @param jobRepository           repository dei metadati Spring Batch
 * @param environment             environment Spring (profili, proprieta')
 * @param applicationZoneId       timezone applicativo (vedi {@link TimezoneConfig})
 * @param schedulerIntervalMillis intervallo dello scheduler, per il calcolo della prossima esecuzione
 * @param entityManager           entity manager usato per le query di dettaglio sulle esecuzioni
 */
public record BatchControllerSupport(
        JobExecutionHelper jobExecutionHelper,
        JobRepository jobRepository,
        Environment environment,
        ZoneId applicationZoneId,
        long schedulerIntervalMillis,
        EntityManager entityManager) {
}
