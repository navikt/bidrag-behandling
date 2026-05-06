-- Optimization: Add indexes for inntekt and inntektspost queries
-- This addresses slow query performance on SELECT inntekt LEFT JOIN inntektspost WHERE behandling_id = $1
-- Issue: Missing index on inntekt.behandling_id (WHERE clause) and potential missing FK index on inntektspost.inntekt_id

-- Index for WHERE clause filtering on inntekt.behandling_id
-- This will significantly improve query performance when filtering inntekter by behandling
CREATE INDEX IF NOT EXISTS idx_inntekt_behandling_id ON inntekt(behandling_id);

-- Index for LEFT JOIN on inntektspost.inntekt_id
-- Ensures efficient join between inntekt and inntektspost tables
CREATE INDEX IF NOT EXISTS idx_inntektspost_inntekt_id ON inntektspost(inntekt_id);

-- Composite index for common query pattern: SELECT inntekt WHERE behandling_id
-- Include frequently accessed columns to avoid additional table lookups
CREATE INDEX IF NOT EXISTS idx_inntekt_behandling_covering
ON inntekt(behandling_id)
INCLUDE (id, ta_med, inntektsrapportering, dato_fom, dato_tom, ident, belop, kilde, gjelder_barn, opprinnelig_fom, opprinnelig_tom);

