package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import org.springframework.data.repository.CrudRepository

interface HusstandsbarnperiodeRepository : CrudRepository<Husstandsbarnperiode, Long>
