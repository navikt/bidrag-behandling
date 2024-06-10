package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Utgift
import org.springframework.data.repository.CrudRepository

interface UtgiftRepository : CrudRepository<Utgift, Long>
