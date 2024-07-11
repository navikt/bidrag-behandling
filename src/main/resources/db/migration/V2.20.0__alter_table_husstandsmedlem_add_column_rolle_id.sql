alter table husstandsmedlem
    add column if not exists rolle_id bigint null;

alter table husstandsmedlem
    alter column fødselsdato drop not null;

