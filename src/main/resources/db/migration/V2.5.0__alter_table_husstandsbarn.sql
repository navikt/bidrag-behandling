alter table barn_i_husstand drop column med_i_saken;

alter table barn_i_husstand add column if not exists kilde character varying(25);
