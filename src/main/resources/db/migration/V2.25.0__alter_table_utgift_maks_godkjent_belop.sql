alter table utgift add column if not exists maks_godkjent_beløp numeric;
alter table utgift add column if not exists maks_godkjent_beløp_kommentar text;