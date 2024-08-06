alter table behandling
    drop column if exists virkningstidspunktbegrunnelse_vedtak_og_notat,
    drop column if exists boforholdsbegrunnelse_vedtak_og_notat,
    drop column if exists inntektsbegrunnelse_vedtak_og_notat;
