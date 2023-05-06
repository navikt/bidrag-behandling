package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface OpplysningerRepository : CrudRepository<Opplysninger, Long> {
    @Query("select o from opplysninger o where o.behandling.id = :behandlingId and o.aktiv = true")
    fun findActiveByBehandlingId(behandlingId: Long): Optional<Opplysninger>
}
