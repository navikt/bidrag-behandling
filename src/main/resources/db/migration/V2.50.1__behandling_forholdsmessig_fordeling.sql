alter table behandling add column if not exists forholdsmessig_fordeling jsonb;

CREATE INDEX if not exists idx_gin_behandling_forholdsmessig_fordeling ON behandling USING GIN (forholdsmessig_fordeling);

alter table rolle add column if not exists forholdsmessig_fordeling jsonb;
CREATE INDEX if not exists idx_gin_rolle_forholdsmessig_fordeling ON rolle USING GIN (forholdsmessig_fordeling);

alter table rolle add column if not exists innkrevingstype text;
alter table rolle add column if not exists stønadstype text;

UPDATE rolle
SET stønadstype = b.stonadstype
FROM behandling b
WHERE rolle.behandling_id = b.id AND b.deleted IS FALSE;