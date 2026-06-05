package it.govpay.notify.batch.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.ec.client.api.NotificaRendicontazioniApi;
import it.govpay.ec.client.api.impl.ApiClient;
import it.govpay.ec.client.beans.NuovaRendicontazione;
import it.govpay.notify.batch.config.EnteApiClientConfig;
import it.govpay.notify.batch.dto.RtNotifyContext;
import it.govpay.notify.batch.gde.service.GdeService;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with Ente RT API.
 * Resolves the RT connector per-applicazione,
 */
@Service
@Slf4j
public class EnteApiService {

	private final GdeService gdeService;
	private final ConnettoreService connettoreService;
	private final ApplicazioneRepository applicazioneRepository;
	private final EnteApiClientConfig enteApiClientConfig;
	private final ObjectMapper clientObjectMapper;

	/** Cache of NotificaRendicontazioniApi instances keyed by connector code */
	private final ConcurrentHashMap<String, NotificaRendicontazioniApi> apiCache = new ConcurrentHashMap<>();

	public EnteApiService(ConnettoreService connettoreService,
						  ApplicazioneRepository applicazioneRepository,
						  EnteApiClientConfig rtApiClientConfig,
						  GdeService gdeService) {
		this.connettoreService = connettoreService;
		this.applicazioneRepository = applicazioneRepository;
		this.enteApiClientConfig = rtApiClientConfig;
		this.gdeService = gdeService;
		this.clientObjectMapper = enteApiClientConfig.createEnteObjectMapper();
	}

	/**
	 * Resolves the connector code for the given applicazione.
	 */
	private String resolveConnectorCode(String codApplicazione) {
		Optional<ApplicazioneEntity> appOpt = applicazioneRepository.findByCodApplicazione(codApplicazione);
		ApplicazioneEntity app = appOpt.orElseThrow(() ->
			new IllegalStateException("Nessuna applicazione trovata per il codice applicazione: " + codApplicazione));

		String codConnettore = app.getCodConnettoreIntegrazione();
		if (codConnettore == null || codConnettore.isBlank()) {
			throw new IllegalStateException(
				"Connettore Notifca RT non configurato per l'applicazione " + app.getCodApplicazione());
		}

		log.debug("Applicazione {} -> Connettore RT: {}", codApplicazione, codConnettore);
		return codConnettore;
	}

	/**
	 * Gets or creates a NotificaRendicontazioniApi instance for the given applicazione.
	 * Uses a cache keyed by connector code to avoid creating duplicate instances
	 * for domains sharing the same intermediary.
	 */
	private NotificaRendicontazioniApi getOrCreateApi(String codApplicazione) {
		String codConnettore = resolveConnectorCode(codApplicazione);
		return apiCache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			// Customize ObjectMapper for pagoPA date handling
			MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(clientObjectMapper);
			restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			Connettore connettore = connettoreService.getConnettore(code);
			ApiClient apiClient = new ApiClient(restTemplate);
			apiClient.setBasePath(connettore.getUrl());

			log.info("Creata istanza NotificaRendicontazioniApi per connettore {} (URL: {})", code, connettore.getUrl());
			return new NotificaRendicontazioniApi(apiClient);
		});
	}

	/**
	 * Returns the ente base URL for the given domain (for GDE event tracking).
	 * Delegates to ConnettoreService which has its own internal caching.
	 */
	private String getBaseUrl(String codApplicazione) {
		String codConnettore = resolveConnectorCode(codApplicazione);
		return connettoreService.getConnettore(codConnettore).getUrl();
	}


	public String notifyRendicontazione(RtNotifyContext rtInfo, CompletableFuture<HttpStatusCode> statusCodeFuture) throws RestClientException {
		log.debug("Notifica ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
		OffsetDateTime dataStart = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime dataEnd = null;
		String enteBaseUrl = getBaseUrl(rtInfo.getCodApplicazione());


		String jsonRequest = null;
		ResponseEntity<Void> response = null;
		try {
			NuovaRendicontazione rndInfo = new NuovaRendicontazione();
			rndInfo.setIdDominio(rtInfo.getTaxCode());
			rndInfo.setIuv(rtInfo.getIuv());
			rndInfo.setIur(rtInfo.getIur());
			rndInfo.setIndice(BigDecimal.valueOf(rtInfo.getIndice()));
			rndInfo.setImporto(rtInfo.getImporto());
			rndInfo.setEsito(mapEsito(rtInfo.getEsito()));
			rndInfo.setData(rtInfo.getData());
			rndInfo.setIdFlusso(rtInfo.getIdFlusso());
			rndInfo.setDataFlusso(rtInfo.getDataFlusso());
			rndInfo.setTrn(rtInfo.getTrn());
			rndInfo.setDataRegolamento(rtInfo.getDataRegolamento());
			rndInfo.setDataOraPubblicazione(rtInfo.getDataOraPubblicazione());
			rndInfo.setDataOraAggiornamento(rtInfo.getDataOraAggiornamento());
			rndInfo.setIdPsp(rtInfo.getIdPsp());
			rndInfo.setBicRiversamento(rtInfo.getBicRiversamento());
			rndInfo.setRevisione(rtInfo.getRevisione());

			jsonRequest = clientObjectMapper.writeValueAsString(rndInfo);

			response = getOrCreateApi(rtInfo.getCodApplicazione()).notificaRendicontazioneWithHttpInfo(rndInfo);
			statusCodeFuture.complete(response.getStatusCode());
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
		} catch (HttpClientErrorException.BadRequest e) {
			// 400 Bad Request
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.warn("Notifica errata: taxCode {} - iur {} - iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			statusCodeFuture.complete(HttpStatus.BAD_REQUEST);
			gdeService.saveNotifyRndKo(rtInfo, jsonRequest, response, e, dataStart, dataEnd, enteBaseUrl);
			return "Notifica errata";
		} catch (HttpClientErrorException.Unauthorized e) {
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.warn("Servizio non autorizzato {} - iur {} - iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			statusCodeFuture.complete(HttpStatus.UNAUTHORIZED);
			gdeService.saveNotifyRndKo(rtInfo, jsonRequest, response, e, dataStart, dataEnd, enteBaseUrl);
			return "Non autorizzato";
		} catch (HttpClientErrorException.Forbidden e) {
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.warn("Servizio negato {} - iur {} - iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
			statusCodeFuture.complete(HttpStatus.FORBIDDEN);
			gdeService.saveNotifyRndKo(rtInfo, jsonRequest, response, e, dataStart, dataEnd, enteBaseUrl);
			return "Accesso negato";
		} catch (RestClientException e) {
			// Altri errori
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.error("Errore durante la notifica della ricevuta: taxCode {} - iur {} - iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv(), e);
			statusCodeFuture.complete(HttpStatus.INTERNAL_SERVER_ERROR);
			gdeService.saveNotifyRndKo(rtInfo, jsonRequest, response, e, dataStart, dataEnd, enteBaseUrl);
			return "Internal error";
		} catch (JsonProcessingException e) {
			dataEnd = OffsetDateTime.now(ZoneOffset.UTC);
			log.error("Errore durante la notifica della ricevuta: taxCode {} - iur {} - iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv(), e);
			statusCodeFuture.complete(HttpStatus.BAD_REQUEST);
			gdeService.saveNotifyRndKo(rtInfo, jsonRequest, response, null, dataStart, dataEnd, enteBaseUrl);
			return "Fail to convert object to json";
		}

		// 200 OK: ricevuta notificata
		log.debug("Notificata ricevuta per l'organizzazione {} con iur {} e iuv {}", rtInfo.getTaxCode(), rtInfo.getIur(), rtInfo.getIuv());
		gdeService.saveNotifyRndOk(rtInfo, jsonRequest, response, dataStart, dataEnd, enteBaseUrl);
		return null;
	}

	/**
	 * Mappa il codice esito numerico (DB) sull'enum stringa dell'API ec-client.
	 * <p>Codici da specifica pagoPA flussi di rendicontazione:
	 * 0=ESEGUITO, 3=REVOCATO, 4=ESEGUITO_STANDIN,
	 * 8=ESEGUITO_STANDIN_SENZA_RPT, 9=ESEGUITO_SENZA_RPT.
	 */
	private NuovaRendicontazione.EsitoEnum mapEsito(int esito) {
		return switch (esito) {
			case 0 -> NuovaRendicontazione.EsitoEnum.ESEGUITO;
			case 3 -> NuovaRendicontazione.EsitoEnum.REVOCATO;
			case 4 -> NuovaRendicontazione.EsitoEnum.ESEGUITO_STANDIN;
			case 8 -> NuovaRendicontazione.EsitoEnum.ESEGUITO_STANDIN_SENZA_RPT;
			case 9 -> NuovaRendicontazione.EsitoEnum.ESEGUITO_SENZA_RPT;
			default -> throw new IllegalArgumentException("Codice esito rendicontazione non riconosciuto: " + esito);
		};
	}

	/**
	 * Svuota la cache delle istanze API per forzare la ricreazione al prossimo utilizzo.
	 */
	public void clearCache() {
		apiCache.clear();
		connettoreService.clearCache();
		log.info("Cache connettori RT svuotata");
	}
}
