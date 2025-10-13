@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.consumer.dto

import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.math.BigDecimal
import java.time.LocalDate

data class HentBPsÅpneSøknaderRequest(
    val personidentBP: String,
)

data class HentBPsÅpneSøknaderResponse(
    val åpneSøknader: List<ÅpenSøknadDto> = emptyList(),
)

data class ÅpenSøknadDto(
    val saksnummer: String,
    val søknadsid: String,
    val stønadstype: String,
    val behandlingsid: String?,
    val søknadMottattDato: LocalDate,
    val søknadFomDato: LocalDate?,
    val søktAvType: SøktAvType,
    val partISøknadListe: List<PartISøknad> = emptyList(),
) {
    val bidragsmottaker get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER.name }
    val bidragspliktig get() = partISøknadListe.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER.name }
    val barn get() = partISøknadListe.filter { it.rolletype == Rolletype.BARN.name }
}

data class PartISøknad(
    val personident: String?,
    val rolletype: String,
    val innbetaltBeløp: BigDecimal? = BigDecimal.ZERO,
    val gebyr: Boolean = false,
)
