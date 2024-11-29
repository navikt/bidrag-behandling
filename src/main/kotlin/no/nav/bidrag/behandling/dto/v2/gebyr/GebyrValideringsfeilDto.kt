package no.nav.bidrag.behandling.dto.v2.gebyr

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.transformers.behandling.tilDto

fun Behandling.validerGebyr() =
    roller
        .filter { it.harGebyrsøknad }
        .map {
            GebyrValideringsfeilDto(
                gjelder = it.tilDto(),
                måBestemmeGebyr = avslag != null && it.manueltOverstyrtGebyr?.ilagtGebyr == null,
                manglerBegrunnelse =
                    if (it.manueltOverstyrtGebyr?.overstyrGebyr == true) {
                        it.manueltOverstyrtGebyr?.begrunnelse.isNullOrEmpty()
                    } else {
                        false
                    },
            )
        }.filter { it.harFeil }

data class GebyrValideringsfeilDto(
    val gjelder: RolleDto,
    val måBestemmeGebyr: Boolean,
    val manglerBegrunnelse: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse || måBestemmeGebyr
}
