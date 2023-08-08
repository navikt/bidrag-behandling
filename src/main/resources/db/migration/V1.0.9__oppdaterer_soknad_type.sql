ALTER TABLE BEHANDLING
    DROP COLUMN SOKNAD_TYPE,
    ADD COLUMN SOKNAD_TYPE TEXT DEFAULT 'FASTSETTELSE';

ALTER TABLE BEHANDLING
    ALTER COLUMN SOKNAD_TYPE SET DEFAULT NOT NULL;

ALTER TABLE BEHANDLING
    DROP COLUMN BEHANDLING_TYPE,
    ADD COLUMN BEHANDLING_TYPE TEXT DEFAULT 'FORSKUDD';

ALTER TABLE BEHANDLING
    ALTER COLUMN BEHANDLING_TYPE SET DEFAULT NOT NULL;

DROP TYPE SOKNAD_TYPE CASCADE;

DROP TYPE OPPLYSNINGER_TYPE CASCADE;

DROP TYPE BEHANDLING_TYPE CASCADE;

