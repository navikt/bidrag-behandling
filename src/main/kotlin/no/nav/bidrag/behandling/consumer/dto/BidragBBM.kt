@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.consumer.dto

import no.nav.bidrag.domene.enums.vedtak.Stønadstype

data class ÅpenSøknadDto(
    val saksnummer: String,
    val søknadsid: String,
    val stønadstype: String,
    val behandlingsid: String?,
    val personidentBP: String,
    val personidentBM: String?,
    val personidentSøknadsbarnListe: List<String>,
)
