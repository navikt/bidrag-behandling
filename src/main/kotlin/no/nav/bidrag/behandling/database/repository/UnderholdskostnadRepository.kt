package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import org.springframework.data.repository.CrudRepository

interface UnderholdskostnadRepository : CrudRepository<Underholdskostnad, Long>
