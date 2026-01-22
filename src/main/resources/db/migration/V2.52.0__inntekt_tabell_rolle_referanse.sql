alter table inntekt add column if not exists rolle_id bigint REFERENCES rolle(id);
alter table inntekt add column if not exists gjelder_barn_rolle_id bigint REFERENCES rolle(id);
CREATE INDEX IF NOT EXISTS idx_inntekt_rolle_id ON inntekt(rolle_id);
CREATE INDEX IF NOT EXISTS idx_inntekt_gjelder_barn_rolle_id ON inntekt(gjelder_barn_rolle_id);

UPDATE inntekt i
SET rolle_id = r.id
FROM rolle r WHERE i.rolle_id IS NULL
  AND r.behandling_id = i.behandling_id
  AND r.ident = i.ident;

UPDATE inntekt i
SET gjelder_barn_rolle_id = r.id
FROM rolle r WHERE i.gjelder_barn_rolle_id IS NULL
  AND i.gjelder_barn IS NOT NULL
  AND r.behandling_id = i.behandling_id
  AND r.ident = i.gjelder_barn;