package no.nav.bidrag.behandling.transformers.inntekt

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import java.math.RoundingMode

fun OppdatereManuellInntekt.tilInntekt(inntekt: Inntekt): Inntekt {
    inntekt.type = this.type
    inntekt.belop = this.beløp
    inntekt.datoFom = this.datoFom
    inntekt.datoTom = this.datoTom
    inntekt.gjelderBarn = this.gjelderBarn?.verdi
    inntekt.kilde = Kilde.MANUELL
    inntekt.taMed = this.taMed
    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp,
                    inntektstype = this.inntektstype,
                    kode = this.type.toString(),
                ),
            )
    }
    return inntekt
}

fun OppdatereManuellInntekt.tilInntekt(behandling: Behandling): Inntekt {
    val inntekt =
        Inntekt(
            type = this.type,
            belop = this.beløp,
            datoFom = this.datoFom,
            datoTom = this.datoTom,
            ident = this.ident.verdi,
            gjelderBarn = this.gjelderBarn?.verdi,
            kilde = Kilde.MANUELL,
            taMed = this.taMed,
            id = this.id,
            behandling = behandling,
        )

    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp,
                    inntektstype = this.inntektstype,
                    kode = this.type.toString(),
                ),
            )
    }

    return inntekt
}

fun Set<Inntektspost>.tilInntektspostDtoV2() =
    this.map {
        InntektspostDtoV2(
            kode = it.kode,
            visningsnavn = finnVisningsnavn(it.kode),
            inntektstype = it.inntektstype,
            beløp = it.beløp,
        )
    }

fun SummertMånedsinntekt.tilInntektDtoV2(gjelder: String) =
    InntektDtoV2(
        id = -1,
        taMed = false,
        rapporteringstype = Inntektsrapportering.AINNTEKT,
        beløp = sumInntekt.setScale(0, RoundingMode.HALF_UP),
        ident = Personident(gjelder),
        kilde = Kilde.OFFENTLIG,
        inntektsposter =
            inntektPostListe.map {
                InntektspostDtoV2(
                    kode = it.kode,
                    visningsnavn = it.visningsnavn,
                    inntektstype = it.inntekstype,
                    beløp = it.beløp.setScale(0, RoundingMode.HALF_UP),
                )
            }.toSet(),
        inntektstyper = emptySet(),
        datoFom = gjelderÅrMåned.atDay(1),
        datoTom = gjelderÅrMåned.atEndOfMonth(),
        opprinneligFom = gjelderÅrMåned.atDay(1),
        opprinneligTom = gjelderÅrMåned.atEndOfMonth(),
        gjelderBarn = null,
    )

fun List<Inntekt>.tilInntektDtoV2() =
    this.map {
        InntektDtoV2(
            id = it.id,
            taMed = it.taMed,
            rapporteringstype = it.type,
            beløp = it.belop,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            ident = Personident(it.ident),
            gjelderBarn = it.gjelderBarn?.let { it1 -> Personident(it1) },
            kilde = it.kilde,
            inntektsposter = it.inntektsposter.tilInntektspostDtoV2().toSet(),
            inntektstyper = it.type.inneholderInntektstypeListe.toSet(),
            opprinneligFom = it.opprinneligFom,
            opprinneligTom = it.opprinneligTom,
        )
    }
