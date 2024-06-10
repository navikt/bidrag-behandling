package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentBearbeidetInntekterForType
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsbarnGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.dto.v2.behandling.InntektspostEndringDto
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandIkkeAktivGrunnlagDto
import no.nav.bidrag.behandling.transformers.ainntekt12Og3Måneder
import no.nav.bidrag.behandling.transformers.ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.filtrerUtHistoriskeInntekter
import no.nav.bidrag.behandling.transformers.inntekt.tilIkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektspostEndring
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDate
import java.time.LocalDateTime

fun erInntektsposterEndret(
    inntektsposter: Set<Inntektspost>,
    nyeInntekstposter: List<InntektPost>,
): Boolean {
    if (inntektsposter.isEmpty() && nyeInntekstposter.isEmpty()) return false
    if (inntektsposter.size != nyeInntekstposter.size) return true
    return nyeInntekstposter.sortedBy { it.kode }.any { nyInntekstpost ->
        erInntektspostEndret(nyInntekstpost, inntektsposter)
    }
}

fun erInntektspostEndret(
    nyInntekstpost: InntektPost,
    eksisterendePoster: Set<Inntektspost>,
): Boolean {
    val eksisterende =
        eksisterendePoster.sortedBy { it.kode }.find {
            (it.inntektstype != null && it.inntektstype == nyInntekstpost.inntekstype) || it.kode == nyInntekstpost.kode
        }
    return eksisterende == null || eksisterende.beløp.nærmesteHeltall != nyInntekstpost.beløp.nærmesteHeltall
}

fun mapTilInntektspostEndringer(
    nyeInntektsposter: Set<InntektPost>,
    eksisterendePoster: Set<Inntektspost>,
): Set<InntektspostEndringDto> {
    val endringer =
        nyeInntektsposter.map {
            if (eksisterendePoster.none { eksisterende -> it.erLik(eksisterende) }) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.NY)
            } else if (erInntektspostEndret(it, eksisterendePoster)) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.ENDRING)
            } else {
                null
            }
        }
    val slettetPoster =
        eksisterendePoster.map {
            if (nyeInntektsposter.none { ny -> ny.erLik(it) }) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.SLETTET)
            } else {
                null
            }
        }

    return (endringer + slettetPoster).filterNotNull().toSet()
}

fun InntektPost.erLik(inntektPost: Inntektspost): Boolean {
    return kode == inntektPost.kode && inntekstype == inntektPost.inntektstype
}

fun List<Grunnlag>.hentEndringerBoforhold(
    aktiveGrunnlag: List<Grunnlag>,
    virkniningstidspunkt: LocalDate,
    husstandsbarn: Set<Husstandsbarn>,
    rolle: Rolle,
): Set<HusstandsbarnGrunnlagDto> {
    val aktiveBoforholdsdata =
        aktiveGrunnlag.hentAlleBearbeidaBoforhold(virkniningstidspunkt, husstandsbarn, rolle).toSet()
    // Hent første for å finne innhentet tidspunkt
    val nyeBoforholdsgrunnlag = find { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    val nyeBoforholdsdata = hentAlleBearbeidaBoforhold(virkniningstidspunkt, husstandsbarn, rolle).toSet()

    return nyeBoforholdsdata.finnEndringerBoforhold(
        virkniningstidspunkt,
        aktiveBoforholdsdata,
        nyeBoforholdsgrunnlag?.innhentet ?: LocalDateTime.now(),
    )
}

fun Set<BoforholdResponse>.finnEndringerBoforhold(
    virkniningstidspunkt: LocalDate,
    aktivBoforholdData: Set<BoforholdResponse>,
    innhentetTidspunkt: LocalDateTime = LocalDateTime.now(),
) = groupBy { it.relatertPersonPersonId }.map { (barnId, oppdaterGrunnlag) ->
    val aktivGrunnlag = aktivBoforholdData.filter { it.relatertPersonPersonId == barnId }
    if (aktivGrunnlag.erLik(oppdaterGrunnlag, virkniningstidspunkt)) return@map null
    oppdaterGrunnlag.tilHusstandsbarnGrunnlagDto(barnId, innhentetTidspunkt)
}.filterNotNull().toSet()

private fun List<BoforholdResponse>.tilHusstandsbarnGrunnlagDto(
    barnId: String?,
    innhentetTidspunkt: LocalDateTime,
) = HusstandsbarnGrunnlagDto(
    map {
        HusstandsbarnGrunnlagDto.HusstandsbarnGrunnlagPeriodeDto(
            it.periodeFom,
            it.periodeTom,
            it.bostatus,
        )
    }.toSet(),
    barnId,
    innhentetTidspunkt,
)

fun List<Grunnlag>.hentEndringerSivilstand(
    aktiveGrunnlag: List<Grunnlag>,
    virkniningstidspunkt: LocalDate,
): SivilstandIkkeAktivGrunnlagDto? {
    return try {
        val aktivSivilstandGrunnlag = aktiveGrunnlag.find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val nySivilstandGrunnlagBearbeidet = find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val nySivilstandGrunnlag = find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }
        val aktiveSivilstandsdata =
            aktivSivilstandGrunnlag.konvertereData<List<Sivilstand>>()
                ?.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkniningstidspunkt)
        val nyeSivilstandsdata =
            nySivilstandGrunnlagBearbeidet.konvertereData<List<Sivilstand>>()
                ?.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkniningstidspunkt)
        if (aktiveSivilstandsdata != null && nyeSivilstandsdata != null &&
            !nyeSivilstandsdata.erLik(
                aktiveSivilstandsdata,
            )
        ) {
            return SivilstandIkkeAktivGrunnlagDto(
                sivilstand =
                    nyeSivilstandsdata.map {
                        SivilstandDto(
                            null,
                            it.periodeFom,
                            it.periodeTom,
                            it.sivilstandskode,
                            Kilde.OFFENTLIG,
                        )
                    },
                innhentetTidspunkt = nySivilstandGrunnlagBearbeidet!!.innhentet,
                grunnlag =
                    nySivilstandGrunnlag?.konvertereData<List<SivilstandGrunnlagDto>>()
                        ?.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(virkniningstidspunkt)
                        ?.toSet() ?: emptySet(),
            )
        }
        return null
    } catch (e: Exception) {
        // TODO: Midlertidlig fiks til V2 av sivilstand beregning brukes
        secureLogger.error(e) { "Det skjedde en feil ved mapping av sivilstand grunnlagdifferanse" }
        null
    }
}

fun List<Sivilstand>.erLik(other: List<Sivilstand>): Boolean {
    if (size != other.size) return false
    return sortedBy { it.periodeFom }.all { sivilstand ->
        other.sortedBy { it.periodeFom }.any {
            it.periodeFom == sivilstand.periodeFom && it.periodeTom == sivilstand.periodeTom &&
                it.sivilstandskode == sivilstand.sivilstandskode
        }
    }
}

fun List<BoforholdResponse>.erLik(
    other: List<BoforholdResponse>,
    virkniningstidspunkt: LocalDate,
): Boolean {
    if (this.size != other.size) return false

    fun BoforholdResponse.justertDatoFom() =
        if (virkniningstidspunkt.isAfter(LocalDate.now())) {
            maxOf(fødselsdato, virkniningstidspunkt.withDayOfMonth(1))
        } else {
            maxOf(virkniningstidspunkt.withDayOfMonth(1), periodeFom)
        }
    return this.all { boforhold ->
        other.any {
            it.justertDatoFom() == boforhold.justertDatoFom() && it.periodeTom == boforhold.periodeTom &&
                it.bostatus == boforhold.bostatus
        }
    }
}

fun List<Grunnlag>.hentEndringerInntekter(
    rolle: Rolle,
    inntekter: Set<Inntekt>,
    type: Grunnlagsdatatype,
): Set<IkkeAktivInntektDto> {
    val inntekterRolle = inntekter.filter { it.ident == rolle.ident }.filtrerUtHistoriskeInntekter()

    val oppdatertGrunnlag = hentBearbeidetInntekterForType(type, rolle.ident!!)?.filtrerUtHistoriskeInntekter()
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
            val erBeløpEndret =
                eksisterendeInntekt.belop.nærmesteHeltall != grunnlag.sumInntekt.nærmesteHeltall
            val er12MndOg3MndPeriodeEndret =
                eksisterendeInntekt.opprinneligPeriode != null && ainntekt12Og3Måneder.contains(eksisterendeInntekt.type) &&
                    eksisterendeInntekt.opprinneligPeriode != grunnlag.periode
            if (erBeløpEndret || er12MndOg3MndPeriodeEndret ||
                erInntektsposterEndret(
                    eksisterendeInntekt.inntektsposter,
                    grunnlag.inntektPostListe,
                )
            ) {
                grunnlag.tilIkkeAktivInntektDto(
                    rolle.ident!!,
                    GrunnlagInntektEndringstype.ENDRING,
                    innhentetTidspunkt,
                    eksisterendeInntekt,
                )
            } else {
                grunnlag.tilIkkeAktivInntektDto(
                    rolle.ident!!,
                    GrunnlagInntektEndringstype.INGEN_ENDRING,
                    innhentetTidspunkt,
                    eksisterendeInntekt,
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
    } else if (ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt.contains(type)) {
        grunnlag.inntektRapportering == type && opprinneligPeriode!! == grunnlag.periode
    } else {
        opprinneligPeriode!! == grunnlag.periode
    }
}
