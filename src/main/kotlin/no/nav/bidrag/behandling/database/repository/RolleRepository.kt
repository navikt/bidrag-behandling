package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Rolle
import org.springframework.data.repository.CrudRepository

interface RolleRepository : CrudRepository<Rolle, Long>
