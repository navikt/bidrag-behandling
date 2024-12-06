package no.nav.bidrag.behandling.transformers.gebyr

import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.ManueltOverstyrGebyrDto

fun RolleManueltOverstyrtGebyr.tilDto() =
    if (overstyrGebyr) {
        ManueltOverstyrGebyrDto(begrunnelse, ilagtGebyr)
    } else {
        null
    }
