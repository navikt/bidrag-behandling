package no.nav.bidrag.behandling.transformers.samvær

import no.nav.bidrag.behandling.database.datamodell.Samværskalkulator
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.tid.Datoperiode

data class OppdaterSamværDto(
    val samværId: Long,
    val gjelderBarn: String,
    val periode: OppdaterSamværPeriodeDto,
)

data class OppdaterSamværPeriodeDto(
    val id: Long? = null,
    val periode: Datoperiode,
    val samværsklasse: Samværsklasse,
    val beregning: Samværskalkulator? = null,
)
