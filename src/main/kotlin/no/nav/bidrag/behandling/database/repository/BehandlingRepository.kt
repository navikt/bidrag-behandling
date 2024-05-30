package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Behandling
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface BehandlingRepository : CrudRepository<Behandling, Long> {
    fun findBehandlingById(id: Long): Optional<Behandling>

    fun findFirstBySoknadsid(soknadsId: Long): Behandling?

    // Ikke erstatt meg med "delete(behandling)"
    // Midlertidlig løsning ved testing.
    // "delete(behandling)" sletter inntekter/grunnlag/husstandsbarn/sivilstand som gjør at det vanskelig å gjenskape det testerne har behandlet før sletting
    // Behandling slettes hvis vedtak fattes gjennom Bisys
    @Modifying
    @Query("update behandling set deleted = true, slettet_tidspunkt = now() where id = :behandlingsid", nativeQuery = true)
    fun logiskSlett(behandlingsid: Long)
}
