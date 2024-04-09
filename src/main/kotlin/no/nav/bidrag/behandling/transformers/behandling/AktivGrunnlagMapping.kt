package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsbarnGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.ainntekt12Og3Måneder
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.inntekt.tilIkkeAktivInntektDto
import no.nav.bidrag.boforhold.response.BoforholdBeregnet
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDateTime

fun erInntektsposterEndret(
    inntektsposter: Set<Inntektspost>,
    nyeInntekstposter: List<InntektPost>,
): Boolean {
    if (inntektsposter.isEmpty() && nyeInntekstposter.isEmpty()) return false
    if (inntektsposter.size != nyeInntekstposter.size) return true
    return nyeInntekstposter.any { nyInntekstpost ->
        val eksisterende =
            inntektsposter.find {
                (it.inntektstype != null && it.inntektstype == nyInntekstpost.inntekstype) || it.kode == nyInntekstpost.kode
            }
        eksisterende == null || eksisterende.beløp.setScale(0) != nyInntekstpost.beløp.setScale(0)
    }
}

private fun List<Grunnlag>.hentBearbeidetInntekterForType(
    type: Grunnlagsdatatype,
    ident: String,
) = find {
    it.type == type && it.erBearbeidet && it.rolle.ident == ident
}.konverterData<SummerteInntekter<SummertÅrsinntekt>>()

fun List<Grunnlag>.hentEndringerBoforhold(aktiveGrunnlag: List<Grunnlag>): Set<HusstandsbarnGrunnlagDto> {
    val aktivBoforholdGrunnlag = aktiveGrunnlag.find { it.type == Grunnlagsdatatype.BOFORHOLD }
    val aktivBoforholdData = aktivBoforholdGrunnlag.konverterData<List<BoforholdBeregnet>>()
    val nyBoforholdGrunnlag = find { it.type == Grunnlagsdatatype.BOFORHOLD }
    val nyBoforholdData = nyBoforholdGrunnlag.konverterData<List<BoforholdBeregnet>>()
    return nyBoforholdData?.groupBy { it.relatertPersonPersonId }?.map { (barnId, oppdaterGrunnlag) ->
        val aktivGrunnlag = aktivBoforholdData?.filter { it.relatertPersonPersonId == barnId } ?: emptyList()
        if (aktivGrunnlag.erLik(oppdaterGrunnlag)) return@map null
        HusstandsbarnGrunnlagDto(
            oppdaterGrunnlag.map {
                HusstandsbarnGrunnlagDto.HusstandsbarnGrunnlagPeriodeDto(
                    it.periodeFom,
                    it.periodeTom,
                    it.bostatus,
                )
            }.toSet(),
            barnId,
            nyBoforholdGrunnlag!!.innhentet,
        )
    }?.filterNotNull()?.toSet() ?: emptySet()
}

fun List<BoforholdBeregnet>.erLik(other: List<BoforholdBeregnet>): Boolean {
    if (this.size != other.size) return false
    return this.all { boforhold ->
        other.any { it.periodeFom == boforhold.periodeFom && it.periodeTom == boforhold.periodeTom }
    }
}

fun List<Grunnlag>.hentEndringerInntekter(
    rolle: Rolle,
    inntekter: Set<Inntekt>,
    type: Grunnlagsdatatype,
): Set<IkkeAktivInntektDto> {
    val oppdatertGrunnlag = hentBearbeidetInntekterForType(type, rolle.ident!!)
    val innhentetTidspunkt = find { it.type == type && it.erBearbeidet }?.innhentet ?: LocalDateTime.now()
    val oppdaterteEllerNyInntekter =
        oppdatertGrunnlag?.inntekter?.map { grunnlag ->
            val eksisterendeInntekt =
                inntekter.find {
                    it.kilde == Kilde.OFFENTLIG &&
                        it.erLik(grunnlag)
                }
                    ?: return@map grunnlag.tilIkkeAktivInntektDto(
                        rolle.ident!!,
                        GrunnlagInntektEndringstype.NY,
                        innhentetTidspunkt,
                    )
            val erBeløpEndret = eksisterendeInntekt.belop.setScale(0) != grunnlag.sumInntekt.setScale(0)
            if (erBeløpEndret || erInntektsposterEndret(eksisterendeInntekt.inntektsposter, grunnlag.inntektPostListe)) {
                grunnlag.tilIkkeAktivInntektDto(
                    rolle.ident!!,
                    GrunnlagInntektEndringstype.ENDRING,
                    innhentetTidspunkt,
                    eksisterendeInntekt.id,
                )
            } else {
                grunnlag.tilIkkeAktivInntektDto(
                    rolle.ident!!,
                    GrunnlagInntektEndringstype.INGEN_ENDRING,
                    innhentetTidspunkt,
                    eksisterendeInntekt.id,
                )
            }
        }?.toSet() ?: emptySet()

    val slettetInntekter =
        inntekter
            .filter { it.kilde == Kilde.OFFENTLIG && type.inneholder(it.type) }
            .filter { inntekt ->
                oppdatertGrunnlag?.inntekter
                    ?.none { inntekt.erLik(it) } == true
            }.map { it.tilIkkeAktivInntektDto(GrunnlagInntektEndringstype.SLETTET, LocalDateTime.now()) }

    return oppdaterteEllerNyInntekter
        .filter { it.endringstype != GrunnlagInntektEndringstype.INGEN_ENDRING }.toSet() + slettetInntekter
}

fun Grunnlagsdatatype.inneholder(type: Inntektsrapportering) =
    when (this) {
        Grunnlagsdatatype.KONTANTSTØTTE -> type == Inntektsrapportering.KONTANTSTØTTE
        Grunnlagsdatatype.BARNETILLEGG -> type == Inntektsrapportering.BARNETILLEGG
        Grunnlagsdatatype.SMÅBARNSTILLEGG -> type == Inntektsrapportering.SMÅBARNSTILLEGG
        Grunnlagsdatatype.UTVIDET_BARNETRYGD -> type == Inntektsrapportering.UTVIDET_BARNETRYGD
        else -> !eksplisitteYtelser.contains(type)
    }

fun Inntekt.erLik(grunnlag: SummertÅrsinntekt): Boolean {
    if (opprinneligPeriode == null || type != grunnlag.inntektRapportering) return false
    return if (eksplisitteYtelser.contains(type)) {
        opprinneligPeriode!! == grunnlag.periode
    } else if (ainntekt12Og3Måneder.contains(type)) {
        grunnlag.inntektRapportering == type
    } else {
        opprinneligPeriode!! == grunnlag.periode
    }
}
