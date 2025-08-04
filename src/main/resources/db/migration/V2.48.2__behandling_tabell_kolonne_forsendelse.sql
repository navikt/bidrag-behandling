alter table behandling add column if not exists klagedetaljer jsonb;

CREATE INDEX if not exists idx_gin_behandling_klage_detaljer ON behandling USING GIN (klagedetaljer);

UPDATE behandling
SET klagedetaljer = jsonb_build_object(
    'klageMottattdato', klage_mottattdato,
    'soknadRefId', soknad_ref_id,
    'refVedtaksid', ref_vedtaksid,
    'påklagetVedtak', påklaget_vedtak,
    'opprinneligVirkningstidspunkt', opprinnelig_virkningstidspunkt,
    'opprinneligVedtakstidspunkt', opprinnelig_vedtakstidspunkt,
    'opprinneligVedtakstype', opprinnelig_vedtakstype,
    'fattetDelvedtak', '[]'::jsonb
) where soknad_ref_id is not null or ref_vedtaksid is not null;