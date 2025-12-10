@file:Suppress("ktlint")

package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple
import no.nav.bidrag.behandling.database.datamodell.minified.RolleSimple
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional


interface BehandlingRepository : CrudRepository<Behandling, Long> {
    @Query(
        """
    SELECT new no.nav.bidrag.behandling.database.datamodell.minified.BehandlingSimple(
        b.id,
        b.søktFomDato,
        b.mottattdato,
        b.saksnummer,
        b.vedtakstype,
        b.søknadstype,
        b.omgjøringsdetaljer,
        b.stonadstype,
        b.engangsbeloptype,
        b.forholdsmessigFordeling
    )
    FROM behandling b

    WHERE b.id = :id
"""
    )
    fun findBehandlingSimpleData(id: Long): BehandlingSimple

    @Query(
        """
    SELECT new no.nav.bidrag.behandling.database.datamodell.minified.RolleSimple(r.rolletype, r.ident) FROM rolle r WHERE r.behandling.id= :id
"""
    )
    fun findRolleSimpleData(id: Long): List<RolleSimple>


    fun findBehandlingSimple(id: Long): BehandlingSimple {
        val behandling = findBehandlingSimpleData(id)
        val roller = findRolleSimpleData(id)
        return behandling.copy(roller = roller)
    }

    fun findBehandlingById(id: Long): Optional<Behandling>

    fun findFirstBySoknadsid(soknadsId: Long): Behandling?

    // Ikke erstatt meg med "delete(behandling)"
    // Midlertidlig løsning ved testing.
    // "delete(behandling)" sletter inntekter/grunnlag/husstandsmedlem/sivilstand som gjør at det vanskelig å gjenskape det testerne har behandlet før sletting
    // Behandling slettes hvis vedtak fattes gjennom Bisys
    @Modifying
    @Query("update behandling b set deleted = true, slettet_tidspunkt = now() where b.id = :behandlingsid or (forholdsmessig_fordeling is not null and (b.forholdsmessig_fordeling->>'behandlesAvBehandling'::text = (:behandlingsid)::text))", nativeQuery = true)
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
        "select * from behandling b " +
            "where jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.feilBegrunnelse != null)')" +
            " and jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.antallForsøkOpprettEllerDistribuer < 10)') and b.vedtakstype = 'ALDERSJUSTERING' and b.vedtaksid is not null",
        nativeQuery = true,
    )
    fun hentBehandlingerHvorDistribusjonAvForsendelseFeilet(): List<Behandling>

    @Query(
        "select * from behandling b " +
            "where jsonb_path_exists(b.forsendelse_bestillinger, '\$.bestillinger[*] ? (@.forsendelseId == \$forsendelseId)', jsonb_build_object('forsendelseId', :forsendelseId)) " +
            "and b.vedtakstype = 'ALDERSJUSTERING' and b.vedtaksid is not null",
        nativeQuery = true,
    )
    fun hentBehandlingerSomInneholderBestillingMedForsendelseId(
        @Param("forsendelseId") forsendelseId: Long,
    ): List<Behandling>

//    @Query(
//        """
//            SELECT b.* FROM behandling b JOIN rolle br ON br.behandling_id = b.id
//            WHERE b.stonadstype is not null and br.rolletype = 'BIDRAGSPLIKTIG' AND br.ident = :bpIdent and b.deleted is false and b.vedtak_detaljer is null
//            and b.id != :ignorerBehandling and (b.forholdsmessig_fordeling is null or b.forholdsmessig_fordeling ->> 'erHovedbehandling' = 'true')""",
//        nativeQuery = true,
//    )
    @Query(
        """
            SELECT b.* FROM behandling b JOIN rolle br ON br.behandling_id = b.id 
            WHERE b.stonadstype is not null and br.rolletype = 'BIDRAGSPLIKTIG' AND br.ident = :bpIdent and b.deleted is false and b.vedtak_detaljer is null 
            and b.id != :ignorerBehandling and b.forholdsmessig_fordeling is null""",
        nativeQuery = true,
    )
    fun finnÅpneBidragsbehandlingerForBp(
        @Param("bpIdent") bpIdent: String,
        @Param("ignorerBehandling") ignorerBehandling: Long,
    ): List<Behandling>

    @Query(
        "SELECT CASE WHEN EXISTS (SELECT 1 FROM behandling b WHERE b.id = :behandlingId AND b.forholdsmessigFordeling IS NOT NULL) THEN TRUE ELSE FALSE END",
    )
    fun erIForholdsmessigFordeling(
        @Param("behandlingId") behandlingId: Long,
    ): Boolean

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
          AND b.forholdsmessig_fordeling IS not NULL AND (b.forholdsmessig_fordeling->>'erHovedbehandling') = 'true'
    """,
        nativeQuery = true,
    )
    fun finnÅpneBidragsbehandlingerForBpMedFF(
        @Param("bpIdent") bpIdent: String,
    ): List<Behandling>

    @Query(
        value = """
        SELECT b.* FROM behandling b
        JOIN rolle br ON br.behandling_id = b.id
        WHERE br.rolletype = 'BIDRAGSPLIKTIG'
          AND br.ident = :bpIdent
          AND b.deleted = false
          AND b.vedtakstidspunkt IS NULL
          AND b.forholdsmessig_fordeling IS not NULL AND (b.forholdsmessig_fordeling->>'erHovedbehandling') = 'true'
       limit 1
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
