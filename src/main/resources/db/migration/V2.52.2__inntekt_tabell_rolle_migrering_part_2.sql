-- Migrering kjøres på nytt pga en feil somgjorde at rolle_id ikke ble satt riktig for barn
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
