-- TABLE: UTVIDET_BARNETRYGD

/* ##### RULLE TILBAKE #####

    -- TABLE: PUBLIC.UTVIDET_BARNETRYGD

    -- DROP TABLE IF EXISTS PUBLIC.UTVIDET_BARNETRYGD;

    CREATE TABLE IF NOT EXISTS PUBLIC.UTVIDET_BARNETRYGD
    (
        ID BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY ( INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1 ),
        BEHANDLING_ID BIGINT NOT NULL,
        DELT_BOSTED BOOLEAN,
        DATO_FOM DATE,
        DATO_TOM DATE,
        BELOP NUMERIC NOT NULL DEFAULT 0,
        CONSTRAINT UTVIDETBARNETRYGD_PKEY PRIMARY KEY (ID),
        CONSTRAINT FK_BEHANDLING_ID FOREIGN KEY (BEHANDLING_ID)
            REFERENCES PUBLIC.BEHANDLING (ID) MATCH SIMPLE
            ON UPDATE NO ACTION
            ON DELETE NO ACTION
    )

    TABLESPACE PG_DEFAULT;

    ALTER TABLE IF EXISTS PUBLIC.UTVIDET_BARNETRYGD
        OWNER TO "BIDRAG-BEHANDLING-MAIN";

    GRANT ALL ON TABLE PUBLIC.UTVIDET_BARNETRYGD TO "BIDRAG-BEHANDLING-MAIN";

    GRANT ALL ON TABLE PUBLIC.UTVIDET_BARNETRYGD TO CLOUDSQLIAMUSER;

     DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.1.4';
 */

DROP TABLE UTVIDET_BARNETRYGD;








