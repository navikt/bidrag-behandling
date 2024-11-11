@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.dto.v1.beregning

import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate

data class ResultatRolle(
    val ident: Personident?,
    val navn: String,
    val fødselsdato: LocalDate,
)
