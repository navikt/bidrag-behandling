-- Table: BEHANDLING

/*
    ALTER TABLE BEHANDLING DROP COLUMN GRUNNLAG_SIST_INNHENTET;
    ALTER TABLE BEHANDLING RENAME COLUMN SOKNADSTYPE TO SOKNAD_TYPE;
    ALTER TABLE BEHANDLINGRENAME COLUMN MOTTATTDATO TO MOTTATT_DATO;
    ALTER TABLE BEHANDLING RENAME COLUMN SOKNADSID TO SOKNAD_ID;
    ALTER TABLE BEHANDLING RENAME COLUMN STONADSTYPE TO STONAD_TYPE;
    ALTER TABLE BEHANDLING RENAME COLUMN ENGANGSBELOPTYPE TO ENGANGSBELOP_TYPE;
    ALTER TABLE BEHANDLING RENAME COLUMN VEDTAKSID TO VEDTAK_ID;
    ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGSDATO TO VIRKNINGS_DATO;
    ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGSTIDSPUNKTBEGRUNNELSE_VEDTAK_OG_NOTAT TO VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT;
    ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGSTIDSPUNKTBEGRUNNELSE_KUN_NOTAT TO VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_KUN_I_NOTAT;
    ALTER TABLE BEHANDLING RENAME COLUMN BOFORHOLDSBEGRUNNELSE_VEDTAK_OG_NOTAT TO BOFORHOLD_BEGRUNNELSE_MED_I_VEDTAK_NOTAT;
    ALTER TABLE BEHANDLING RENAME COLUMN BOFORHOLDSBEGRUNNELSE_KUN_I_NOTAT TO INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT;
    ALTER TABLE BEHANDLING RENAME COLUMN INNTEKTSBEGRUNNELSE_VEDTAK_OG_NOTAT TO INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT;
    ALTER TABLE BEHANDLING RENAME COLUMN INNTEKTSBEGRUNNELSE_KUN_I_NOTAT TO INNTEKT_BEGRUNNELSE_KUN_I_NOTAT;
    ALTER TABLE BEHANDLINGRENAME COLUMN GRUNNLAGSPAKKEID TO GRUNNLAGSPAKKE_ID;

     DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '1.0.45';
 */

ALTER TABLE BEHANDLING ADD COLUMN GRUNNLAG_SIST_INNHENTET TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE BEHANDLING RENAME COLUMN SOKNAD_TYPE TO SOKNADSTYPE;
ALTER TABLE BEHANDLING RENAME COLUMN MOTTAT_DATO TO MOTTATTDATO;
ALTER TABLE BEHANDLING RENAME COLUMN SOKNAD_ID TO SOKNADSID;
ALTER TABLE BEHANDLING RENAME COLUMN STONAD_TYPE TO STONADSTYPE;
ALTER TABLE BEHANDLING RENAME COLUMN ENGANGSBELOP_TYPE TO ENGANGSBELOPTYPE;
ALTER TABLE BEHANDLING RENAME COLUMN VEDTAK_ID TO VEDTAKSID;
ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGS_DATO TO VIRKNINGSDATO;
ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TO VIRKNINGSTIDSPUNKTBEGRUNNELSE_VEDTAK_OG_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_KUN_I_NOTAT TO VIRKNINGSTIDSPUNKTBEGRUNNELSE_KUN_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN BOFORHOLD_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TO BOFORHOLDSBEGRUNNELSE_VEDTAK_OG_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN BOFORHOLD_BEGRUNNELSE_KUN_I_NOTAT TO BOFORHOLDSBEGRUNNELSE_KUN_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT TO INNTEKTSBEGRUNNELSE_VEDTAK_OG_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN INNTEKT_BEGRUNNELSE_KUN_I_NOTAT TO INNTEKTSBEGRUNNELSE_KUN_NOTAT;
ALTER TABLE BEHANDLING RENAME COLUMN GRUNNLAGSPAKKE_ID TO GRUNNLAGSPAKKEID;





