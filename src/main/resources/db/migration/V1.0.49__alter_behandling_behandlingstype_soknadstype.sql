-- Table: behandling

/*
  Rollback

  alter table behandling add column behandlingstype text
  alter table behandling rename column vedtakstype to soknadstype;
 */

alter table behandling
    drop column behandlingstype;
alter table behandling
    rename column soknadstype to vedtakstype








