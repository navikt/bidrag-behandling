package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import org.springframework.data.repository.CrudRepository

interface BarnetilsynRepository : CrudRepository<Barnetilsyn, Long>

interface FaktiskTilsynsutgiftRepository : CrudRepository<FaktiskTilsynsutgift, Long>

interface TilleggsstønadRepository : CrudRepository<Tilleggsstønad, Long>

interface UnderholdskostnadRepository: CrudRepository<Underholdskostnad, Long>