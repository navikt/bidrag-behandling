-- Table: INNTEKT

/*
    ALTER TABLE INNTEKT RENAME COLUMN INNTEKTSRAPPORTERING TO INNTEKTSTYPE;
    ALTER TABLE INNTEKT DROP COLUMN KILDE;
    ALTER TABLE INNTEKT DROP COLUMN GJELDER_BARN;
    ALTER TABLE INNTEKT DROP COLUMN OPPRINNELIG_FOM;
    ALTER TABLE INNTEKT DROP COLUMN OPPRINNELIG_TOM;

    DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.1.1';
 */

ALTER TABLE INNTEKT RENAME COLUMN INNTEKTSTYPE TO INNTEKTSRAPPORTERING;
ALTER TABLE INNTEKT ADD COLUMN KILDE VARCHAR(15);
ALTER TABLE INNTEKT ADD COLUMN GJELDER_BARN VARCHAR(11);
ALTER TABLE INNTEKT ADD COLUMN OPPRINNELIG_FOM DATE DEFAULT NULL;
ALTER TABLE INNTEKT ADD COLUMN OPPRINNELIG_TOM DATE DEFAULT NULL;








