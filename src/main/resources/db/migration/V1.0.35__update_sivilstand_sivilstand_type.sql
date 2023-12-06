-- Table: SIVILSTAND

-- Legg til kolonne for å gjøre det mulig å kjøre skriptet flere ganger uten at det feiler
alter table SIVILSTAND
    add column if not exists SIVILSTAND_TYPE text;

alter table SIVILSTAND
    rename column SIVILSTAND_TYPE to SIVILSTAND_TYPE_OLD;

ALTER TABLE SIVILSTAND
    ADD COLUMN if not exists SIVILSTAND TEXT;

UPDATE SIVILSTAND
SET SIVILSTAND = 'GIFT_SAMBOER'
WHERE SIVILSTAND_TYPE_OLD = 'GIFT';

UPDATE SIVILSTAND
SET SIVILSTAND = 'BOR_ALENE_MED_BARN'
WHERE SIVILSTAND_TYPE_OLD = 'BOR_ALENE_MED_BARN';

alter table SIVILSTAND
    drop column SIVILSTAND_TYPE_OLD;

drop type if exists SIVILSTAND_TYPE cascade;
