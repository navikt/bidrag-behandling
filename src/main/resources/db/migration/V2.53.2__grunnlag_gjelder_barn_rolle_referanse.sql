alter table grunnlag add column if not exists gjelder_barn_rolle_id bigint REFERENCES rolle(id);
CREATE INDEX IF NOT EXISTS idx_grunnlag_gjelder_barn_rolle_id ON grunnlag(gjelder_barn_rolle_id);

UPDATE grunnlag g
SET gjelder_barn_rolle_id = r.id
FROM rolle r WHERE g.gjelder_barn_rolle_id IS NULL
  AND g.gjelder IS NOT NULL
  AND r.behandling_id = g.behandling_id
  AND r.ident = g.gjelder;
