package it.govpay.notify.batch;

public class Costanti {
	public static final String LAST_PROCESSED_ID_KEY = "lastProcessedId";

    // Pattern date per serializzazione/deserializzazione JSON
    // Pattern con millisecondi variabili (1-9 cifre) per deserializzazione sicura da pagoPA
    public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI = "yyyy-MM-dd'T'HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]";
    public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]XXX";

    // Pattern per serializzazione date alle API esterne (3 cifre millisecondi senza timezone) 
    public static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    // Pattern per serializzazione date al GDE (3 cifre millisecondi con timezone)
    public static final String PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    // Nome job RT notify
    public static final String RT_NOTIFY_JOB_NAME = "rtNotifyJob";

    // Non sono URI completi (mancano protocollo e host) con il baseUrl configurabile tramite il Connettore nel DB.
    public static final String PATH_NOTIFY_RND = "/rendicontazioni";

    private Costanti() {
        // Costruttore privato per evitare istanziazione
    }

}
