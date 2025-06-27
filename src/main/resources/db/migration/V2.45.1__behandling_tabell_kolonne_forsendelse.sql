alter table behandling add column if not exists forsendelse_bestillinger jsonb;

CREATE INDEX if not exists idx_gin_behandling_forsendelse_bestillinger ON behandling USING GIN (forsendelse_bestillinger);