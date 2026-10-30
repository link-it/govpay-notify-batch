-- Una pendenza pagata e rendicontata, scritta come la scrive govpay-common-pendenze
-- (modello v3, GovPay 3.11): posizione debitoria su documenti, opzione di pagamento,
-- soggetto debitore in soggetti_debitori e segnaposto in versamenti.debitore_*.
-- Su di essa una riscossione, una notifica RICEVUTA da spedire e una rendicontazione da notificare.

INSERT INTO intermediari (id, cod_intermediario, cod_connettore_pdd, denominazione, principal, principal_originale, abilitato)
VALUES (1, '11111111111', 'PDD', 'Intermediario di test', 'intermediario', 'intermediario', true);

INSERT INTO stazioni (id, cod_stazione, password, abilitato, application_code, versione, id_intermediario)
VALUES (1, '11111111111_01', 'password', true, 1, 'V2', 1);

INSERT INTO domini (id, cod_dominio, abilitato, ragione_sociale, intermediato, scarica_fr, aux_digit, id_stazione)
VALUES (1, '12345678901', true, 'Ente di test', true, false, 0, 1);

INSERT INTO utenze (id, principal, principal_originale)
VALUES (1, 'app-v3', 'app-v3');

INSERT INTO applicazioni (id, cod_applicazione, auto_iuv, firma_ricevuta, trusted, id_utenza)
VALUES (1, 'APP_V3', false, '0', false, 1);

INSERT INTO tipi_versamento (id, cod_tipo_versamento, descrizione, abilitato)
VALUES (1, 'IMU', 'Imposta municipale', true);

INSERT INTO tipi_vers_domini (id, id_tipo_versamento, id_dominio)
VALUES (1, 1, 1);

INSERT INTO tipi_tributo (id, cod_tributo)
VALUES (1, 'TRIB-IMU');

INSERT INTO tributi (id, abilitato, id_dominio, id_tipo_tributo)
VALUES (1, true, 1, 1);

INSERT INTO documenti (id, cod_documento, descrizione, id_dominio, id_applicazione)
VALUES (1, 'POS-V3-1', 'Posizione debitoria di test', 1, 1);

INSERT INTO opzioni_pagamento (id, id_opzione_pagamento, tipologia, stato, versione,
        data_creazione, data_ultimo_aggiornamento, id_documento)
VALUES (1, '6f1c2a3e-0b7d-4c55-9a51-3c2d8e4f5a61', 'SOLUZIONE_UNICA', 'ATTIVATA', 0, now(), now(), 1);

INSERT INTO soggetti_debitori (id, ordine, tipo, identificativo, anagrafica, id_documento)
VALUES (1, 0, 'F', 'RSSMRA80A01H501U', 'Mario Rossi', 1),
       (2, 1, 'F', 'VRDGPP80A01H501X', 'Giuseppe Verdi', 1);

INSERT INTO versamenti (id, cod_versamento_ente, importo_totale, stato_versamento, aggiornabile,
        data_creazione, data_ora_ultimo_aggiornamento,
        debitore_identificativo, debitore_anagrafica, src_debitore_identificativo,
        ack, anomalo, importo_pagato, importo_incassato, stato_pagamento, tipo, send_abilitato,
        iuv_versamento, numero_avviso, numero_rata,
        id_tipo_versamento_dominio, id_tipo_versamento, id_dominio, id_applicazione,
        id_documento, id_opzione_pagamento)
VALUES (1, 'PND-V3-1', 100.00, 'ESEGUITO', false,
        now(), now(),
        'VEDERE_SOGGETTI_DEBITORI', 'Vedere tabella soggetti_debitori', 'VEDERE_SOGGETTI_DEBITORI',
        true, false, 100.00, 0, 'PAGATO', 'DOVUTO', false,
        '01000000000000001', '301000000000000001', 1,
        1, 1, 1, 1,
        1, 1);

INSERT INTO singoli_versamenti (id, cod_singolo_versamento_ente, stato_singolo_versamento,
        importo_singolo_versamento, indice_dati, id_versamento, id_dominio, id_tributo)
VALUES (1, 'VOCE-V3-1', 'ESEGUITO', 100.00, 1, 1, 1, 1);

INSERT INTO rpt (id, iuv, ccp, cod_dominio, cod_msg_richiesta, data_msg_richiesta, stato,
        data_aggiornamento_stato, cod_stazione, versione, id_versamento,
        data_msg_ricevuta, cod_esito_pagamento, importo_totale_pagato,
        identificativo_attestante, denominazione_attestante, cod_canale)
VALUES (1, '01000000000000001', 'RCP-V3-1', '12345678901', 'MSG-V3-1', now() - interval '1 day', 'RT_ACCETTATA_PA',
        now() - interval '1 day', '11111111111_01', 'SANP_240', 1,
        now() - interval '1 day', 0, 100.00,
        'PSP001', 'PSP di test', 'CANALE001');

INSERT INTO pagamenti (id, cod_dominio, iuv, indice_dati, importo_pagato, data_acquisizione, iur,
        data_pagamento, stato, tipo, id_rpt, id_singolo_versamento)
VALUES (1, '12345678901', '01000000000000001', 1, 100.00, now() - interval '1 day', 'RCP-V3-1',
        now() - interval '1 day', 'PAGATO', 'ENTRATA', 1, 1);

INSERT INTO notifiche (id, tipo_esito, stato, data_creazione, data_aggiornamento_stato,
        data_prossima_spedizione, tentativi_spedizione, id_applicazione, id_rpt)
VALUES (1, 'RICEVUTA', 'DA_SPEDIRE', now() - interval '1 day', now() - interval '1 day',
        now() - interval '1 day', 0, 1, 1);

INSERT INTO fr (id, cod_psp, cod_dominio, cod_flusso, stato, iur, data_ora_flusso, data_acquisizione,
        obsoleto, revisione, id_dominio)
VALUES (1, 'PSP001', '12345678901', 'FLUSSO-V3-1', 'ACCETTATA', 'TRN-V3-1', now() - interval '1 day', now(),
        false, 1, 1);

INSERT INTO rendicontazioni (id, iuv, iur, stato, indice_dati, importo_pagato, esito,
        notifica_inviata, esegui_recupero_rt, id_fr, id_singolo_versamento)
VALUES (1, '01000000000000001', 'RCP-V3-1', 'OK', 1, 100.00, 0,
        false, false, 1, 1);
