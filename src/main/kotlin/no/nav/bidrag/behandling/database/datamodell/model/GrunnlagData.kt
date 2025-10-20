@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.database.datamodell.model

import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate

data class BpsBarnUtenBidragsak(
    val ident: Personident,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val enhet: String?,
)
