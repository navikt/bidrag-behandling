package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    fun findFirstBySoknadsid(soknadsId: Long): Behandling?

    // Ikke erstatt meg med "delete(behandling)"
    // Midlertidlig løsning ved testing.
    // "delete(behandling)" sletter inntekter/grunnlag/husstandsmedlem/sivilstand som gjør at det vanskelig å gjenskape det testerne har behandlet før sletting
    // Behandling slettes hvis vedtak fattes gjennom Bisys
    @Modifying
    @Query("update behandling set deleted = true, slettet_tidspunkt = now() where id = :behandlingsid", nativeQuery = true)
    fun logiskSlett(behandlingsid: Long)

    @Query("select b.* from behandling b where b.id = :behandlingsid", nativeQuery = true)
    fun hentBehandlingInkludertSlettet(behandlingsid: Long): Behandling?

    @Query("select r.* from rolle r where r.behandling_id = :behandlingsid", nativeQuery = true)
    fun hentRollerInkludertSlettet(behandlingsid: Long): List<Rolle>

    @Query(
        "select b from behandling b where b.vedtaksid is not null and b.notatJournalpostId is null and b.vedtakstidspunkt >= :afterDate and b.vedtakstype != 'ALDERSJUSTERING'",
    )
    fun hentBehandlingerSomManglerNotater(afterDate: LocalDateTime): List<Behandling>

    @Query(
        "select * from behandling b where jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.feilBegrunnelse != null)') and jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.antallForsøkOpprettEllerDistribuer < 10)') and b.vedtakstype = 'ALDERSJUSTERING' and b.vedtaksid is not null",
        nativeQuery = true,
    )
    fun hentBehandlingerHvorDistribusjonAvForsendelseFeilet(): List<Behandling>

    @Query(
        "select * from behandling b where jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.forsendelseId == \$forsendelseId)', jsonb_build_object('forsendelseId', :forsendelseId)) and b.vedtakstype = 'ALDERSJUSTERING' and b.vedtaksid is not null",
        nativeQuery = true,
    )
    fun hentBehandlingerSomInneholderBestillingMedForsendelseId(
        @Param("forsendelseId") forsendelseId: Long,
    ): List<Behandling>

    @Query(
        "SELECT b FROM behandling b JOIN b.roller bp JOIN b.roller barn " +
            "WHERE b.stonadstype is not null and bp.rolletype = 'BIDRAGSPLIKTIG' AND bp.ident = :bpIdent and b.deleted is false and b.vedtakDetaljer is null and b.id != :ignorerBehandling",
    )
    fun finnÅpneBidragsbehandlingerForBp(
        @Param("bpIdent") bpIdent: String,
        @Param("ignorerBehandling") ignorerBehandling: Long,
    ): List<Behandling>

    @Query(
        "SELECT b FROM behandling b JOIN b.roller bp JOIN b.roller barn " +
            "WHERE barn.rolletype = 'BARN' AND barn.ident = :barnIdent and b.deleted is false and b.vedtakDetaljer is null",
    )
    fun finnÅpneBidragsbehandlingerForBarn(
        @Param("barnIdent") barnIdent: String,
    ): List<Behandling>

    @Query(
        value = """
        SELECT b.* FROM behandling b
        JOIN rolle br ON br.behandling_id = b.id
        WHERE br.rolletype = 'BIDRAGSPLIKTIG'
          AND br.ident = :bpIdent
          AND b.deleted = false
          AND b.vedtak_detaljer IS NULL
          AND (b.forholdsmessig_fordeling->>'erHovedbehandling') = 'true'
    """,
        nativeQuery = true,
    )
    fun finnHovedbehandlingForBpVedFF(
        @Param("bpIdent") bpIdent: String,
    ): Behandling?

    @Query(
        value = """
        SELECT b.* FROM behandling b
        WHERE b.deleted = false
          AND ((b.forholdsmessig_fordeling->>'behandlesAvBehandling')::bigint = :behandlingId or b.id = :behandlingId)
    """,
        nativeQuery = true,
    )
    fun finnAlleRelaterteBehandlinger(
        @Param("behandlingId") behandlingId: Long,
    ): List<Behandling>
}
