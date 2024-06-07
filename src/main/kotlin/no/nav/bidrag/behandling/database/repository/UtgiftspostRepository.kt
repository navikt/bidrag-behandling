package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import org.springframework.data.repository.CrudRepository

interface UtgiftspostRepository : CrudRepository<Utgiftspost, Long>
