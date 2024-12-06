package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt

val summertAinntektstyper =
    setOf(
        Inntektsrapportering.AINNTEKT,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
        Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
        Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
    )

val summertYtelsetyper =
    setOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.SMÅBARNSTILLEGG,
        Inntektsrapportering.UTVIDET_BARNETRYGD,
    )

@OptIn(ExperimentalStdlibApi::class)
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
val grunnlagsdataTyperYtelser =
    setOf(
        Grunnlagsdatatype.BARNETILLEGG,
        Grunnlagsdatatype.KONTANTSTØTTE,
        Grunnlagsdatatype.SMÅBARNSTILLEGG,
        Grunnlagsdatatype.UTVIDET_BARNETRYGD,
    )
val List<SummertÅrsinntekt>.skattegrunnlagListe
    get() =
        filter {
            summertSkattegrunnlagstyper.contains(it.inntektRapportering)
        }
val List<SummertÅrsinntekt>.ainntektListe get() = filter { summertAinntektstyper.contains(it.inntektRapportering) }

fun List<InntektPost>.tilInntektspost(inntekt: Inntekt) =
    this
        .map {
            Inntektspost(
                beløp = it.beløp,
                kode = it.kode,
                inntektstype = it.inntekstype,
                inntekt = inntekt,
            )
        }.toMutableSet()

fun SummertÅrsinntekt.tilInntekt(
    behandling: Behandling,
    person: Personident,
): Inntekt {
    val beløp = this.sumInntekt.nærmesteHeltall
    val inntekt =
        Inntekt(
            type = this.inntektRapportering,
            belop = beløp,
            behandling = behandling,
            ident = person.verdi,
            gjelderBarn = this.gjelderBarnPersonId,
            datoFom = null,
            datoTom = null,
            opprinneligFom = this.periode.fom.atDay(1),
            opprinneligTom = this.periode.til?.atEndOfMonth(),
            kilde = Kilde.OFFENTLIG,
            taMed = false,
        )
    inntekt.inntektsposter =
        if (this.inntektRapportering == Inntektsrapportering.BARNETILLEGG) {
            mutableSetOf(
                Inntektspost(
                    kode = this.inntektRapportering.name,
                    beløp = beløp,
                    // TODO: Hentes bare fra pensjon i dag. Dette bør endres når vi henter barnetillegg fra andre kilder
                    inntektstype = Inntektstype.BARNETILLEGG_PENSJON,
                    inntekt = inntekt,
                ),
            )
        } else {
            this.inntektPostListe.tilInntektspost(inntekt)
        }
    return inntekt
}

fun List<SummertÅrsinntekt>.tilInntekt(
    behandling: Behandling,
    person: Personident,
) = this
    .map {
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

operator fun BeregnGrunnlag.plus(grunnlag: List<GrunnlagDto>) =
    copy(
        grunnlagListe = (grunnlagListe + grunnlag).toSet().toList(),
    )

fun Behandling.henteNyesteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolleInnhentetFor: Rolle,
): Grunnlag? =
    grunnlag
        .filter {
            it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()
        .maxByOrNull { it.innhentet }

fun Behandling.henteNyesteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolle: Rolle,
    gjelder: Personident?,
): Grunnlag? =
    grunnlag
        .filter {
            it.type == grunnlagstype.type &&
                it.rolle.id == rolle.id &&
                grunnlagstype.erBearbeidet == it.erBearbeidet &&
                it.gjelder == gjelder?.verdi
        }.toSet()
        .maxByOrNull { it.innhentet }
