-- Table: BARN_I_HUSSTAND_PERIODE

/*
     ALTER TABLE BARN_I_HUSSTAND_PERIODE RENAME COLUMN BOSTATUS TO BO_STATUS;

     DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.37';
 */

ALTER TABLE BARN_I_HUSSTAND_PERIODE RENAME COLUMN BO_STATUS TO BOSTATUS;





