-- Table: UTVIDETBARNETRYGD => UTVIDET_BARNETRYGD

/*
     ALTER TABLE UTVIDET_BARNETRYGD RENAME TO UTVIDETBARNETRYGD;
     ALTER TABLE UTVIDETBARNETRYGD RENAME COLUMN DELT_BOSTED TO DELT_BO_STED;

     DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.38';
 */

ALTER TABLE UTVIDETBARNETRYGD RENAME COLUMN DELT_BO_STED TO DELT_BOSTED;
ALTER TABLE UTVIDETBARNETRYGD RENAME TO UTVIDET_BARNETRYGD;





