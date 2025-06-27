package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
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

    @Query("select b from behandling b where b.vedtaksid is not null and b.notatJournalpostId is null and b.vedtakstidspunkt >= :afterDate")
    fun hentBehandlingerSomManglerNotater(afterDate: LocalDateTime): List<Behandling>

    @Query(
        "select b from behandling b where jsonb_path_exists(b.forsendelseBestillingerJsonString, '\$.bestillinger[*] ? (@.feilBegrunnelse != null)') and b.vedtakstype = 'ALDERSJUSTERING' and b.vedtaksid is not null",
    )
    fun hentBehandlingerHvorDistribusjonAvForsendelseFeilet(): List<Behandling>
}
