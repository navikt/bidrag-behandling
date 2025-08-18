alter table behandling add column if not exists klagedetaljer jsonb;

CREATE INDEX if not exists idx_gin_behandling_klage_detaljer ON behandling USING GIN (klagedetaljer);

UPDATE behandling SET klagedetaljer = jsonb_build_object(
        'klageMottattdato', klage_mottattdato,
        'soknadRefId', soknad_ref_id,
        'refVedtaksid', ref_vedtaksid,
        'påklagetVedtak', ref_vedtaksid,
        'opprinneligVirkningstidspunkt', opprinnelig_virkningstidspunkt,
        'opprinneligVedtakstidspunkt', opprinnelig_vedtakstidspunkt,
        'opprinneligVedtakstype', opprinnelig_vedtakstype
) where soknad_ref_id is not null or ref_vedtaksid is not null;

alter table behandling add column if not exists vedtak_detaljer jsonb;

CREATE INDEX if not exists idx_gin_behandling_vedtak_detaljer ON behandling USING GIN (vedtak_detaljer);

UPDATE behandling SET vedtak_detaljer = jsonb_build_object(
        'vedtaksid', vedtaksid,
        'vedtakFattetAvEnhet', vedtak_fattet_av_enhet,
        'vedtakstidspunkt', vedtakstidspunkt,
        'vedtakFattetAv', vedtak_fattet_av,
        'fattetDelvedtak', '[]'::jsonb
) where vedtaksid is not null;