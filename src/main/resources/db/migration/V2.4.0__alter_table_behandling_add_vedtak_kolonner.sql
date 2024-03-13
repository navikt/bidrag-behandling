alter table behandling
    add column if not exists vedtakstidspunkt timestamp;

alter table behandling
    add column if not exists vedtak_fattet_av text;