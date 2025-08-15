
alter table rolle add column if not exists grunnlag_fra_vedtak_json jsonb default '[]'::jsonb;

CREATE INDEX if not exists idx_gin_rolle_grunnlag_fra_vedtak ON rolle USING GIN (grunnlag_fra_vedtak_json);


UPDATE rolle
SET grunnlag_fra_vedtak_json = jsonb_build_array(jsonb_build_object('vedtak', grunnlag_fra_vedtak, 'aldersjusteringForÅr', null))
WHERE grunnlag_fra_vedtak IS NOT NULL;