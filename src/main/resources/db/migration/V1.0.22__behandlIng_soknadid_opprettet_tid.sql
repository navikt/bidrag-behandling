ALTER TABLE BEHANDLING
    ADD COLUMN IF NOT EXISTS SOKNAD_ID INT not null default -1;
ALTER TABLE BEHANDLING
    ADD COLUMN IF NOT EXISTS SOKNAD_REF_ID INT;
ALTER TABLE BEHANDLING
    ADD COLUMN IF NOT EXISTS OPPRETTET_TIDSPUNKT timestamp DEFAULT now() NOT NULL;