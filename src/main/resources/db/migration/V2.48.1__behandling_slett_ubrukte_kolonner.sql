
alter table behandling drop column if exists virkningstidspunktbegrunnelse_kun_notat;
alter table behandling drop column if exists boforholdsbegrunnelse_kun_notat;
alter table behandling drop column if exists inntektsbegrunnelse_kun_notat;
alter table behandling drop column if exists utgiftsbegrunnelse_kun_notat;

alter table behandling add column if not exists påklaget_vedtak numeric;
update behandling set påklaget_vedtak = ref_vedtaksid where ref_vedtaksid is not null;