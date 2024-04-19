package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentBearbeidetInntekterForType
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsbarnGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandIkkeAktivGrunnlagDto
import no.nav.bidrag.behandling.transformers.ainntekt12Og3Måneder
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.inntekt.tilIkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDateTime

fun erInntektsposterEndret(
    inntektsposter: Set<Inntektspost>,
    nyeInntekstposter: List<InntektPost>,
): Boolean {
    if (inntektsposter.isEmpty() && nyeInntekstposter.isEmpty()) return false
    if (inntektsposter.size != nyeInntekstposter.size) return true
    return nyeInntekstposter.sortedBy { it.kode }.any { nyInntekstpost ->
        val eksisterende =
            inntektsposter.sortedBy { it.kode }.find {
                (it.inntektstype != null && it.inntektstype == nyInntekstpost.inntekstype) || it.kode == nyInntekstpost.kode
            }
        eksisterende == null || eksisterende.beløp.nærmesteHeltall != nyInntekstpost.beløp.nærmesteHeltall
    }
}

fun List<Grunnlag>.hentEndringerBoforhold(aktiveGrunnlag: List<Grunnlag>): Set<HusstandsbarnGrunnlagDto> {
    val aktivBoforholdGrunnlag = aktiveGrunnlag.find { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    val aktivBoforholdData = aktivBoforholdGrunnlag.konverterData<List<BoforholdResponse>>()
    val nyBoforholdGrunnlag = find { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    val nyBoforholdData = nyBoforholdGrunnlag.konverterData<List<BoforholdResponse>>()
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

fun List<Grunnlag>.hentEndringerSivilstand(aktiveGrunnlag: List<Grunnlag>): SivilstandIkkeAktivGrunnlagDto? {
    return try {
        val aktivSivilstandGrunnlag = aktiveGrunnlag.find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val aktivSivilstandData = aktivSivilstandGrunnlag.konverterData<SivilstandBeregnet>()
        val nySivilstandGrunnlagBearbeidet = find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val nySivilstandGrunnlag = find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }
        val nySivilstandData = nySivilstandGrunnlagBearbeidet.konverterData<SivilstandBeregnet>()
        if (aktivSivilstandData != null && nySivilstandData != null && !nySivilstandData.erLik(aktivSivilstandData)) {
            return SivilstandIkkeAktivGrunnlagDto(
                sivilstand =
                    nySivilstandData.sivilstandListe.map {
                        SivilstandDto(
                            null,
                            it.periodeFom,
                            it.periodeTom,
                            it.sivilstandskode,
                            Kilde.OFFENTLIG,
                        )
                    },
                status = nySivilstandData.status,
                innhentetTidspunkt = nySivilstandGrunnlagBearbeidet!!.innhentet,
                grunnlag = nySivilstandGrunnlag?.konverterData<Set<SivilstandGrunnlagDto>>() ?: emptySet(),
            )
        }
        return null
    } catch (e: Exception) {
        // TODO: Midlertidlig fiks til V2 av sivilstand beregning brukes
        secureLogger.error(e) { "Det skjedde en feil ved mapping av sivilstand grunnlagdifferanse" }
        null
    }
}

fun SivilstandBeregnet.erLik(other: SivilstandBeregnet): Boolean {
    if (status != other.status) return false
    if (sivilstandListe.size != other.sivilstandListe.size) return false
    sivilstandListe.sortedBy { it.periodeFom }.forEachIndexed { index, sivilstand ->
        val otherSivilstand = other.sivilstandListe[index]
        if (otherSivilstand.periodeFom == sivilstand.periodeFom &&
            otherSivilstand.periodeTom == sivilstand.periodeTom &&
            otherSivilstand.sivilstandskode == sivilstand.sivilstandskode
        ) {
            return true
        }
    }
    return false
}

fun List<BoforholdResponse>.erLik(other: List<BoforholdResponse>): Boolean {
    if (this.size != other.size) return false
    return this.all { boforhold ->
        other.any {
            it.periodeFom == boforhold.periodeFom && it.periodeTom == boforhold.periodeTom &&
                it.bostatus == boforhold.bostatus
        }
    }
}

fun List<Grunnlag>.hentEndringerInntekter(
    rolle: Rolle,
    inntekter: Set<Inntekt>,
    type: Grunnlagsdatatype,
): Set<IkkeAktivInntektDto> {
    val inntekterRolle = inntekter.filter { it.ident == rolle.ident }
    val oppdatertGrunnlag = hentBearbeidetInntekterForType(type, rolle.ident!!)
    val innhentetTidspunkt = find { it.type == type && it.erBearbeidet }?.innhentet ?: LocalDateTime.now()
    val oppdaterteEllerNyInntekter =
        oppdatertGrunnlag?.inntekter?.map { grunnlag ->
            val eksisterendeInntekt =
                inntekterRolle.find {
                    it.kilde == Kilde.OFFENTLIG &&
                        it.erLik(grunnlag)
                }
                    ?: return@map grunnlag.tilIkkeAktivInntektDto(
                        rolle.ident!!,
                        GrunnlagInntektEndringstype.NY,
                        innhentetTidspunkt,
                    )
//            val erPeriodeEndret = eksisterendeInntekt.opprinneligPeriode != grunnlag.periode
            val erBeløpEndret =
                eksisterendeInntekt.belop.nærmesteHeltall != grunnlag.sumInntekt.nærmesteHeltall
            if (erBeløpEndret ||
                erInntektsposterEndret(
                    eksisterendeInntekt.inntektsposter,
                    grunnlag.inntektPostListe,
                )
            ) {
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
        inntekterRolle
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
