ALTER TABLE BEHANDLING
    ADD COLUMN SOKNAD_ID INT not null default -1;
ALTER TABLE BEHANDLING
    ADD COLUMN SOKNAD_REF_ID INT;