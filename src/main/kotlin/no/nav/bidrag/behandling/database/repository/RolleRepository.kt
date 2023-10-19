package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Rolle
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface RolleRepository : CrudRepository<Rolle, Long> {
    @Query("select r from rolle r where r.behandling.id = :behandlingId")
    fun findRollerByBehandlingId(behandlingId: Long): List<Rolle>
}
