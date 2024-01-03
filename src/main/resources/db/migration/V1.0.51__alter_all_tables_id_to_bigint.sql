-- Table: Alle

/* -- Rollback --

    ALTER TABLE BEHANDLING ALTER COLUMN ID TYPE INT;
    ALTER TABLE BEHANDLING ALTER COLUMN vedtaksid TYPE INT;
    ALTER TABLE BEHANDLING ALTER COLUMN soknadsid TYPE INT;
    ALTER TABLE BEHANDLING ALTER COLUMN soknad_ref_id TYPE INT;
    ALTER TABLE BEHANDLING ALTER COLUMN soknad_ref_id TYPE INT;
    ALTER TABLE BEHANDLING ALTER COLUMN grunnlagspakkeid TYPE INT;
    ALTER TABLE BARNETILLEGG ALTER COLUMN ID TYPE INT;
    ALTER TABLE BARNETILLEGG ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE barn_i_husstand ALTER COLUMN ID TYPE INT;
    ALTER TABLE barn_i_husstand ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE barn_i_husstand_periode ALTER COLUMN ID TYPE INT;
    ALTER TABLE barn_i_husstand_periode ALTER COLUMN barn_i_husstand_id TYPE INT;
    ALTER TABLE grunnlag ALTER COLUMN ID TYPE INT;
    ALTER TABLE grunnlag ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE inntekt ALTER COLUMN ID TYPE INT;
    ALTER TABLE inntekt ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE inntektspost ALTER COLUMN ID TYPE INT;
    ALTER TABLE inntektspost ALTER COLUMN INNTEKT_ID TYPE INT;
    ALTER TABLE rolle ALTER COLUMN ID TYPE INT;
    ALTER TABLE rolle ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE sivilstand ALTER COLUMN ID TYPE INT;
    ALTER TABLE sivilstand ALTER COLUMN BEHANDLING_ID TYPE INT;
    ALTER TABLE utvidet_barnetrygd ALTER COLUMN ID TYPE INT;
    ALTER TABLE utvidet_barnetrygd ALTER COLUMN BEHANDLING_ID TYPE INT;

    DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.51';

 */

ALTER TABLE BEHANDLING ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE BEHANDLING ALTER COLUMN vedtaksid TYPE BIGINT;
ALTER TABLE BEHANDLING ALTER COLUMN soknadsid TYPE BIGINT;
ALTER TABLE BEHANDLING ALTER COLUMN soknad_ref_id TYPE BIGINT;
ALTER TABLE BEHANDLING ALTER COLUMN soknad_ref_id TYPE BIGINT;
ALTER TABLE BEHANDLING ALTER COLUMN grunnlagspakkeid TYPE BIGINT;
ALTER TABLE BARNETILLEGG ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE BARNETILLEGG ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE barn_i_husstand ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE barn_i_husstand ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE barn_i_husstand_periode ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE barn_i_husstand_periode ALTER COLUMN barn_i_husstand_id TYPE BIGINT;
ALTER TABLE grunnlag ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE grunnlag ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE inntekt ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE inntekt ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE inntektspost ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE inntektspost ALTER COLUMN INNTEKT_ID TYPE BIGINT;
ALTER TABLE rolle ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE rolle ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE sivilstand ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE sivilstand ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
ALTER TABLE utvidet_barnetrygd ALTER COLUMN ID TYPE BIGINT;
ALTER TABLE utvidet_barnetrygd ALTER COLUMN BEHANDLING_ID TYPE BIGINT;
