delete from barn_i_husstand_periode;
delete from barn_i_husstand;

alter table barn_i_husstand alter column kilde set not null;
alter table barn_i_husstand rename column foedselsdato to fødselsdato;
alter table barn_i_husstand rename to husstandsbarn;
alter table barn_i_husstand_periode rename column barn_i_husstand_id to husstandsbarn_id;
alter table barn_i_husstand_periode rename to husstandsbarnperiode;