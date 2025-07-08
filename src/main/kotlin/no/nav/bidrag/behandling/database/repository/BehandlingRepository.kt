package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
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
}
