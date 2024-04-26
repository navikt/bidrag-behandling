package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import org.springframework.data.repository.CrudRepository

interface HusstandsbarnRepository : CrudRepository<Husstandsbarn, Long>
