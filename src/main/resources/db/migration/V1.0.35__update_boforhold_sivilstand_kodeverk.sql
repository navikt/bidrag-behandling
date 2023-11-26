-- Table: SIVILSTAND

alter table SIVILSTAND
    rename column SIVILSTAND_TYPE to SIVILSTAND_TYPE_OLD;

ALTER TABLE SIVILSTAND
    ADD COLUMN SIVILSTAND TEXT;

UPDATE SIVILSTAND
SET SIVILSTAND = 'GIFT_SAMBOER'
WHERE sivilstand_type_old = 'GIFT';

UPDATE SIVILSTAND
SET SIVILSTAND = 'BOR_ALENE_MED_BARN'
WHERE sivilstand_type_old = 'BOR_ALENE_MED_BARN';

alter table SIVILSTAND
    drop column SIVILSTAND_TYPE_OLD;

drop type if exists SIVILSTAND_TYPE cascade;

-- Table: BARN_I_HUSSTAND_PERIODE
alter table BARN_I_HUSSTAND_PERIODE
    add column if not exists BOSTATUS text default 'MED_FORELDER' not null;

-- Legg til kolonne for å gjøre det mulig å kjøre skriptet flere ganger uten at det feiler
alter table BARN_I_HUSSTAND_PERIODE
    add column if not exists BO_STATUS text;

UPDATE BARN_I_HUSSTAND_PERIODE
SET BOSTATUS = 'MED_FORELDER'
WHERE BO_STATUS = 'REGISTRERT_PA_ADRESSE';

UPDATE BARN_I_HUSSTAND_PERIODE
SET BOSTATUS = 'IKKE_MED_FORELDER'
WHERE BO_STATUS = 'IKKE_REGISTRERT_PA_ADRESSE';

alter table BARN_I_HUSSTAND_PERIODE
    drop column if exists BO_STATUS;

