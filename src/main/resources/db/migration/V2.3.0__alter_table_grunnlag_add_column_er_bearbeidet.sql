delete from grunnlag;

update behandling set grunnlag_sist_innhentet = null;

alter table grunnlag
    add column if not exists er_bearbeidet boolean not null;