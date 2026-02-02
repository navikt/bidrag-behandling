alter table inntektspost add column if not exists beløpstype text;
alter table tilleggsstønad add column if not exists månedsbeløp numeric;
alter table tilleggsstønad alter column dagsats drop not null;