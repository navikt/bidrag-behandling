package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Samvær
import org.springframework.data.repository.CrudRepository

interface SamværRepository : CrudRepository<Samvær, Long> {
    fun findSamværByRolleIdent(ident: String)
}
