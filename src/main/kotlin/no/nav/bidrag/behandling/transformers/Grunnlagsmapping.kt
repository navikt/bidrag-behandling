package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.math.RoundingMode

val summertAinntektstyper =
    setOf(
        Inntektsrapportering.AINNTEKT,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
    )

val summertYtelsetyper =
    setOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.BARNETILSYN,
        Inntektsrapportering.SMÅBARNSTILLEGG,
        Inntektsrapportering.UTVIDET_BARNETRYGD,
    )
val summertSkattegrunnlagstyper =
    Inntektsrapportering.entries
        .filter { !it.kanLeggesInnManuelt && it.hentesAutomatisk }
        .filter { !summertAinntektstyper.contains(it) }
        .filter { !summertYtelsetyper.contains(it) }

val inntekterOgYtelser =
    setOf(
        Grunnlagsdatatype.BARNETILLEGG,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
    )
val List<SummertÅrsinntekt>.skattegrunnlagListe
    get() =
        filter {
            summertSkattegrunnlagstyper.contains(it.inntektRapportering)
        }
val List<SummertÅrsinntekt>.ainntektListe get() = filter { summertAinntektstyper.contains(it.inntektRapportering) }

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

fun SummertÅrsinntekt.tilInntekt(
    behandling: Behandling,
    person: Personident,
): Inntekt {
    val inntekt =
        Inntekt(
            type = this.inntektRapportering,
            belop = this.sumInntekt.setScale(0, RoundingMode.HALF_UP),
            behandling = behandling,
            ident = person.verdi,
            gjelderBarn = this.gjelderBarnPersonId,
            datoFom = this.periode.fom.atDay(1),
            datoTom = this.periode.til?.atEndOfMonth(),
            opprinneligFom = this.periode.fom.atDay(1),
            opprinneligTom = this.periode.til?.atEndOfMonth(),
            kilde = Kilde.OFFENTLIG,
            taMed = false,
        )
    inntekt.inntektsposter = this.inntektPostListe.tilInntektspost(inntekt)
    return inntekt
}

fun List<SummertÅrsinntekt>.tilInntekt(
    behandling: Behandling,
    person: Personident,
) = this.map {
    it.tilInntekt(behandling, person)
}.toMutableSet()

fun Inntektsrapportering.tilGrunnlagsdataType() =
    when (this) {
        Inntektsrapportering.BARNETILLEGG -> Grunnlagsdatatype.BARNETILLEGG
        Inntektsrapportering.BARNETILSYN -> Grunnlagsdatatype.BARNETILSYN
        Inntektsrapportering.UTVIDET_BARNETRYGD -> Grunnlagsdatatype.UTVIDET_BARNETRYGD
        Inntektsrapportering.SMÅBARNSTILLEGG -> Grunnlagsdatatype.SMÅBARNSTILLEGG
        Inntektsrapportering.KONTANTSTØTTE -> Grunnlagsdatatype.KONTANTSTØTTE
        else -> Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER
    }
