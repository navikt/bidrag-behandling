-- Table: SIVILSTAND

/*
    ALTER TABLE SIVILSTAND
         DROP COLUMN KILDE;

     DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.32';
 */

ALTER TABLE SIVILSTAND
    ADD COLUMN KILDE VARCHAR(25);
