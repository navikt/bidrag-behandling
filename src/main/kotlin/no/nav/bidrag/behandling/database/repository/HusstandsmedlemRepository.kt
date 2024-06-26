package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import org.springframework.data.repository.CrudRepository

interface HusstandsmedlemRepository : CrudRepository<Husstandsmedlem, Long>
