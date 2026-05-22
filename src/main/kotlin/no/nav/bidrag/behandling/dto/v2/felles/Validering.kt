@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v2.felles

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.tid.Datoperiode

data class OverlappendePeriode(
    val periode: Datoperiode,
    @get:Schema(description = "Teknisk id på inntekter som overlapper")
    val idListe: MutableSet<Long>,
)
