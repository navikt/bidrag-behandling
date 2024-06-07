package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import org.springframework.data.repository.CrudRepository

interface SivilstandRepository : CrudRepository<Sivilstand, Long>
