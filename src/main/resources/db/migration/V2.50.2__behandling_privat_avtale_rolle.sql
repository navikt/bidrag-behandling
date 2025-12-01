alter table rolle add column if not exists innkreves_fra_dato date;
alter table privat_avtale add column if not exists grunnlag_fra_vedtak_json jsonb;
alter table privat_avtale add column if not exists utenlandsk boolean default false;
alter table privat_avtale_periode add column if not exists valutakode text;
alter table privat_avtale_periode add column if not exists samværsklasse text;

CREATE INDEX if not exists idx_gin_privat_avtale_grunnlag_fra_vedtak ON privat_avtale USING GIN (grunnlag_fra_vedtak_json);
