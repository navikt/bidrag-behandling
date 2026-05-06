-- Optimization: Add indexes for rolle and grunnlag join queries
-- Addresses slow query: SELECT rolle LEFT JOIN grunnlag ... LEFT JOIN notat WHERE rolle.behandling_id=$1 AND deleted=$2
-- notat.rolle_id already indexed (idx_notat_rolle_id from V2.23.0)

-- Composite index for the WHERE clause: behandling_id + deleted
-- Covers the primary filter pattern: WHERE behandling_id=$1 AND deleted=$2
CREATE INDEX IF NOT EXISTS idx_rolle_behandling_id_deleted ON rolle(behandling_id, deleted);

-- Index for LEFT JOIN on grunnlag.rolle_id
-- V2.1.0 added the rolle_id column but never created an index for it
CREATE INDEX IF NOT EXISTS idx_grunnlag_rolle_id ON grunnlag(rolle_id);

-- Optimization: Add index for grunnlag behandling_id lookup
-- Addresses slow query: SELECT grunnlag WHERE behandling_id=$1
-- grunnlag.rolle_id already indexed (idx_grunnlag_rolle_id from V2.54.1)

-- Index for WHERE clause filtering on grunnlag.behandling_id
-- grunnlag rows can be numerous (one per grunnlagstype per aktiv/innhentet cycle),
-- so filtering without an index causes a full table scan
CREATE INDEX IF NOT EXISTS idx_grunnlag_behandling_id ON grunnlag(behandling_id);

