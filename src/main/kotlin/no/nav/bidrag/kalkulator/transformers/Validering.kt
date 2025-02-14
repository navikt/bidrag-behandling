/*
package no.nav.bidrag.behandling.transformers

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val log = KotlinLogging.logger {}

private val inntekstrapporteringerSomKreverInnteksttype = listOf(Inntektsrapportering.BARNETILLEGG)

val Behandling.utgiftCuttofDato get() = mottattdato.minusYears(1)

fun Behandling.erDatoForUtgiftForeldet(utgiftDato: LocalDate) = utgiftDato < utgiftCuttofDato

fun Utgiftspost.erUtgiftForeldet() = utgift.behandling.erDatoForUtgiftForeldet(dato)

fun Behandling.validerKanOppdatere() {
    if (erVedtakFattet) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er fattet for behandling og kan derfor ikke oppdateres",
        )
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun OpprettBehandlingRequest.valider() {
    val feilliste = mutableListOf<String>()
    (stønadstype == null && engangsbeløpstype == null).ifTrue {
        feilliste.add("Stønadstype eller engangsbeløpstype må settes")
    }
    if (erSærbidrag()) {
        when {
            roller.none { it.rolletype == Rolletype.BIDRAGSPLIKTIG } ->
                feilliste.add("Behandling av typen $engangsbeløpstype må ha en rolle av typen ${Rolletype.BIDRAGSPLIKTIG}")

            roller.none { it.rolletype == Rolletype.BIDRAGSMOTTAKER } ->
                feilliste.add("Behandling av typen $engangsbeløpstype må ha en rolle av typen ${Rolletype.BIDRAGSMOTTAKER}")

            kategori?.kategori.isNullOrEmpty() ->
                feilliste.add(
                    "Kategori må settes for ${Engangsbeløptype.SÆRBIDRAG}",
                )

            Særbidragskategori.entries.none { it.name == kategori?.kategori } ->
                feilliste.add(
                    "Kategori ${kategori?.kategori} er ikke en gyldig særbidrag kategori",
                )

            kategori?.kategori == Særbidragskategori.ANNET.name && kategori.beskrivelse.isNullOrEmpty() ->
                feilliste.add("Beskrivelse må settes hvis kategori er ${Særbidragskategori.ANNET}")
        }
    }
    roller
        .any { it.rolletype == Rolletype.BARN && (it.ident?.verdi.isNullOrBlank() && it.navn.isNullOrBlank()) }
        .ifTrue { feilliste.add("Barn må ha enten ident eller navn") }

    roller
        .any { it.rolletype != Rolletype.BARN && it.ident?.verdi.isNullOrBlank() }
        .ifTrue { feilliste.add("Voksne må ha ident") }

    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved opprettelse av behandling: ${feilliste.joinToString(", ")}",
        )
    }
}
*/
