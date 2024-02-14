package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt

fun List<InntektPost>.tilInntektspost(inntekt: Inntekt) =
    this.map {
        Inntektspost(
            beløp = it.beløp,
            kode = it.kode,
            visningsnavn = it.visningsnavn,
            inntektstype = it.inntekstype,
            inntekt = inntekt,
        )
    }.toMutableSet()

fun List<SummertÅrsinntekt>.tilInntekt(
    behandling: Behandling,
    person: Personident,
) = this.map {
    val inntekt =
        Inntekt(
            inntektsrapportering = it.inntektRapportering,
            belop = it.sumInntekt,
            behandling = behandling,
            ident = person.verdi,
            gjelderBarn = it.gjelderBarnPersonId,
            datoFom = it.periode.fom.atDay(1),
            datoTom = it.periode.til?.minusMonths(1)?.atEndOfMonth(),
            opprinneligFom = it.periode.fom.atDay(1),
            opprinneligTom = it.periode.til?.minusMonths(1)?.atEndOfMonth(),
            kilde = Kilde.OFFENTLIG,
            taMed = false,
        )
    inntekt.inntektsposter = it.inntektPostListe.tilInntektspost(inntekt)
    inntekt
}.toMutableSet()

fun List<SummertMånedsinntekt>.konvertereTilInntekt(
    behandling: Behandling,
    person: Personident,
) = this.map {
    val inntekt =
        Inntekt(
            inntektsrapportering = Inntektsrapportering.AINNTEKT,
            belop = it.sumInntekt,
            behandling = behandling,
            ident = person.verdi,
            datoFom = it.gjelderÅrMåned.atDay(1),
            datoTom = it.gjelderÅrMåned.minusMonths(1).atEndOfMonth(),
            opprinneligFom = it.gjelderÅrMåned.atDay(1),
            opprinneligTom = it.gjelderÅrMåned.minusMonths(1).atEndOfMonth(),
            kilde = Kilde.OFFENTLIG,
            taMed = false,
        )
    inntekt.inntektsposter = it.inntektPostListe.tilInntektspost(inntekt)
    inntekt
}.toMutableSet()
