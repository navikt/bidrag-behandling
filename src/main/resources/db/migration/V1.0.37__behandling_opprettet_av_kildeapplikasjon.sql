-- Table: BEHANDLING
alter table BEHANDLING
    add column if not exists opprettet_av text default '' not null;

alter table BEHANDLING
    add column if not exists opprettet_av_navn text;

alter table BEHANDLING
    add column if not exists kildeapplikasjon text default 'bisys' not null;