package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.boforhold.dto.Kilde
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface InntektRepository : CrudRepository<Inntekt, Long> {
    fun findByIdAndKilde(
        id: Long,
        kilde: Kilde,
    ): Optional<Inntekt>
}
