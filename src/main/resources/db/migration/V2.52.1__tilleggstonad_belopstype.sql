alter table tilleggsstønad add column if not exists beløpstype text default 'DAGSATS';
alter table inntektspost alter column beløpstype set default 'ÅRSBELØP';
alter table tilleggsstønad alter column dagsats set not null;
alter table tilleggsstønad drop column if exists månedsbeløp;