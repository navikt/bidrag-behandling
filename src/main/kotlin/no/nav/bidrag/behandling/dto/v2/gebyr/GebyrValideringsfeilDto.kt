package no.nav.bidrag.behandling.dto.v2.gebyr

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.SøknadDetaljerDto
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.behandling.tilSøknadsdetaljerDto

fun Behandling.validerGebyr() =
    roller
        .filter { it.harGebyrsøknad }
        .flatMap { rolle ->
            rolle.hentEllerOpprettGebyr().gebyrSøknader.map {
                GebyrValideringsfeilDto(
                    gjelder = rolle.tilDto(),
                    søknad = rolle.tilSøknadsdetaljerDto(it.søknadsid),
                    manglerBegrunnelse =
                        if (it.manueltOverstyrtGebyr?.overstyrGebyr == true) {
                            it.manueltOverstyrtGebyr?.begrunnelse.isNullOrEmpty()
                        } else {
                            false
                        },
                )
            }
        }.filter { it.harFeil }

data class GebyrValideringsfeilDto(
    val gjelder: RolleDto,
    val søknad: SøknadDetaljerDto,
    val manglerBegrunnelse: Boolean,
) {
    @get:JsonIgnore
    val harFeil
        get() = manglerBegrunnelse
}
