delete from grunnlag;

alter table grunnlag
    add column if not exists er_bearbeidet boolean not null;