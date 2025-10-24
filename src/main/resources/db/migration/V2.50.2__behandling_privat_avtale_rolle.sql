alter table rolle add column if not exists innkreves_fra_dato date;
alter table privat_avtale_periode add column if not exists valutakode text;
alter table privat_avtale_periode add column if not exists samværsklasse text;