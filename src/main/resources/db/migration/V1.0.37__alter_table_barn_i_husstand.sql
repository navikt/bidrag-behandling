-- Table: BARN_I_HUSSTAND

/*
    ALTER TABLE BARN_I_HUSSTAND RENAME COLUMN FOEDSELSDATO TO FOEDSELS_DATO;

    DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.37';
 */

ALTER TABLE BARN_I_HUSSTAND RENAME COLUMN FOEDSELS_DATO TO FOEDSELSDATO;








