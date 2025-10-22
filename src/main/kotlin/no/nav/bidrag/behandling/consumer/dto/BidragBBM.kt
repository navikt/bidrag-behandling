@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.consumer.dto

import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.math.BigDecimal
import java.time.LocalDate

data class OpprettSøknadRequest(
    val saksnummer: String,
    val stønadstype: Stønadstype,
    val behandlingsid: String?,
    val enhet: String,
    val søknadFomDato: LocalDate,
    val barnListe: List<Barn>,
)

data class Barn(
    val personident: String,
    val innkreving: Boolean,
)

data class OpprettSøknaderResponse(
    val søknadsid: String,
)

data class HentBPsÅpneSøknaderRequest(
    val personidentBP: String,
)

data class HentBPsÅpneSøknaderResponse(
    val åpneSøknader: List<ÅpenSøknadDto> = emptyList(),
)

data class ÅpenSøknadDto(
    val behandlingstype: Behandlingstype,
    val saksnummer: String,
    val søknadsid: String,
    val stønadstype: Stønadstype,
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

data class OpprettSøknaderRequest(
    val opprettSøknadListe: List<OpprettSøknad>,
)

data class OpprettSøknad(
    val saksnummer: String,
    val enhet: String,
    val behandlingsid: String?,
    val barnListe: List<Barn>,
)

data class OpprettedeSøknader(
    val saksnummer: String?,
    val søknadsidListe: List<String> = emptyList(),
)

data class OppdaterBehandlingsidRequest(
    val eksisterendeBehandlingsid: String? = null,
    val nyBehandlingsid: String,
    val søknadsid: String,
)
