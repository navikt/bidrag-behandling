alter table behandling add column if not exists behandlingstema text;
alter table rolle add column if not exists behandlingstema text;
alter table rolle add column if not exists behandlingstatus text;