ALTER TABLE BEHANDLING
    DROP COLUMN BEGRUNNELSE_MED_I_VEDTAK_NOTAT,
    DROP COLUMN BEGRUNNELSE_KUN_I_NOTAT,
    ADD COLUMN VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TEXT DEFAULT NULL,
    ADD COLUMN VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_KUN_I_NOTAT TEXT DEFAULT NULL,
    ADD COLUMN BOFORHOLD_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TEXT DEFAULT NULL,
    ADD COLUMN BOFORHOLD_BEGRUNNELSE_KUN_I_NOTAT TEXT DEFAULT NULL,
    ADD COLUMN INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TEXT DEFAULT NULL,
    ADD COLUMN INNTEKT_BEGRUNNELSE_KUN_I_NOTAT TEXT DEFAULT NULL;