package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentAlleAktiv
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.database.datamodell.henteBearbeidaInntekterForType
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsmedlemGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.dto.v2.behandling.InntektspostEndringDto
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandIkkeAktivGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.StønadTilBarnetilsynIkkeAktiveGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.transformers.ainntekt12Og3Måneder
import no.nav.bidrag.behandling.transformers.ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.inntekt.tilIkkeAktivInntektDto
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektspostEndring
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.underhold.tilBarnetilsyn
import no.nav.bidrag.behandling.transformers.underhold.tilStønadTilBarnetilsynDtos
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
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
            if (eksisterendePoster.none { eksisterende -> it.erDetSammeSom(eksisterende) }) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.NY)
            } else if (erInntektspostEndret(it, eksisterendePoster)) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.ENDRING)
            } else {
                null
            }
        }
    val slettetPoster =
        eksisterendePoster.map {
            if (nyeInntektsposter.none { ny -> ny.erDetSammeSom(it) }) {
                it.tilInntektspostEndring(GrunnlagInntektEndringstype.SLETTET)
            } else {
                null
            }
        }

    return (endringer + slettetPoster).filterNotNull().toSet()
}

fun InntektPost.erDetSammeSom(inntektPost: Inntektspost): Boolean = kode == inntektPost.kode && inntekstype == inntektPost.inntektstype

fun Set<Bostatus>.erLik(detAndreSettet: Set<Bostatus>): Boolean {
    if (size != detAndreSettet.size) return false
    return asSequence().sortedBy { it.periodeFom }.all { gjeldendeData ->
        detAndreSettet.asSequence().sortedBy { it.periodeFom }.any {
            it.periodeFom == gjeldendeData.periodeFom &&
                it.periodeTom == gjeldendeData.periodeTom &&
                it.bostatus == gjeldendeData.bostatus
        }
    }
}

fun List<Grunnlag>.henteEndringerIArbeidsforhold(alleAktiveGrunnlag: List<Grunnlag>): Set<ArbeidsforholdGrunnlagDto> {
    val aktiveArbeidsforholdsgrunnlag =
        alleAktiveGrunnlag.filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type && !it.erBearbeidet }
    val aktiveData =
        aktiveArbeidsforholdsgrunnlag
            .asSequence()
            .mapNotNull { it.konvertereData<Set<ArbeidsforholdGrunnlagDto>>() }
            .flatten()
            .toSet()
    val nyeData =
        this
            .asSequence()
            .filter { Grunnlagsdatatype.ARBEIDSFORHOLD == it.type && !it.erBearbeidet }
            .mapNotNull { it.konvertereData<Set<ArbeidsforholdGrunnlagDto>>() }
            .flatten()
            .toSet()

    if (aktiveData.isNotEmpty() && nyeData.isNotEmpty() && !nyeData.erDetSammeSom(aktiveData)) {
        return nyeData
    }
    return emptySet()
}

fun List<Grunnlag>.henteEndringerIBarnetilsyn(
    aktiveGrunnlag: Set<Grunnlag>,
    behandling: Behandling,
): StønadTilBarnetilsynIkkeAktiveGrunnlagDto? {
    fun Personident.erSøknadsbarn() = behandling.søknadsbarn.find { it.personident == this } != null

    fun Behandling.henteUnderholdskostnadPersonident(personident: Personident) =
        this.underholdskostnader.find {
            it.person.personident == personident
        }

    val innhentaForRolle = behandling.bidragsmottaker!!

    val virkningsdato = behandling.virkningstidspunktEllerSøktFomDato
    val aktiveBarnetilsyn = aktiveGrunnlag.hentAlleBearbeidaBarnetilsyn(virkningsdato, innhentaForRolle)
    val nyeBarnetilsyn =
        toSet()
            .hentAlleBearbeidaBarnetilsyn(virkningsdato, innhentaForRolle)
            .filter { Personident(it.barnPersonId).erSøknadsbarn() }
            .toSet()

    val aktiveBarnetilsynsdata =
        aktiveBarnetilsyn
            .filtrerePerioderEtterVirkningstidspunkt(virkningsdato)
            .groupBy { it.barnPersonId }
            .map { (personidentBarn, barnetilsyn) ->
                Personident(personidentBarn) to barnetilsyn
            }.toMap()

    val nyeBarnetilsynsdata: Map<Personident, Set<BarnetilsynGrunnlagDto>> =
        nyeBarnetilsyn
            .filtrerePerioderEtterVirkningstidspunkt(virkningsdato)
            .groupBy { it.barnPersonId }
            .map { (personidentBarn, barnetilsyn) ->
                Personident(personidentBarn) to barnetilsyn.toSet()
            }.toMap()

    val nyeBarnetilsynsdataTilknyttetSøknadsbarn =
        nyeBarnetilsynsdata.filter { (k, v) ->
            k.erSøknadsbarn() &&
                v.isNotEmpty() &&
                !aktiveBarnetilsynsdata[k].isNullOrEmpty() &&
                !v.toSet().erLik(aktiveBarnetilsynsdata[k]!!.toSet(), virkningsdato)
        }

    if (aktiveBarnetilsynsdata.values.isNotEmpty() && nyeBarnetilsynsdataTilknyttetSøknadsbarn.values.isNotEmpty()) {
        return StønadTilBarnetilsynIkkeAktiveGrunnlagDto(
            stønadTilBarnetilsyn =
                nyeBarnetilsynsdataTilknyttetSøknadsbarn
                    .map {
                        it.key to
                            it.value
                                .tilBarnetilsyn(behandling.henteUnderholdskostnadPersonident(it.key)!!)
                                .toSet()
                                .tilStønadTilBarnetilsynDtos()
                    }.toMap(),
            grunnlag =
                nyeBarnetilsyn
                    .groupBy { it.barnPersonId }
                    .map { (personidentBarn, barnetilsyn) ->
                        Personident(personidentBarn) to barnetilsyn.toSet()
                    }.toMap(),
            innhentetTidspunkt =
                find { Grunnlagsdatatype.BARNETILSYN == it.type && it.erBearbeidet }?.innhentet
                    ?: LocalDateTime.now(),
        )
    }
    return null
}

fun List<Grunnlag>.henteEndringerIBoforhold(
    aktiveGrunnlag: List<Grunnlag>,
    behandling: Behandling,
): Set<HusstandsmedlemGrunnlagDto> {
    val virkniningstidspunkt = behandling.virkningstidspunktEllerSøktFomDato
    val husstandsmedlemmer = behandling.husstandsmedlem
    val rolle = Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(behandling)!!

    val aktiveBoforholdsdata =
        aktiveGrunnlag.hentAlleBearbeidaBoforhold(virkniningstidspunkt, husstandsmedlemmer, rolle).toSet()
    // Hent første for å finne innhentet tidspunkt
    val nyeBoforholdsgrunnlag = find { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    val nyeBoforholdsdata = hentAlleBearbeidaBoforhold(virkniningstidspunkt, husstandsmedlemmer, rolle).toSet()

    return nyeBoforholdsdata.finnEndringerBoforhold(
        virkniningstidspunkt,
        aktiveBoforholdsdata,
        nyeBoforholdsgrunnlag?.innhentet ?: LocalDateTime.now(),
    )
}

fun Set<BoforholdResponseV2>.finnEndringerBoforhold(
    virkniningstidspunkt: LocalDate,
    aktivBoforholdData: Set<BoforholdResponseV2>,
    innhentetTidspunkt: LocalDateTime = LocalDateTime.now(),
) = groupBy { it.gjelderPersonId }
    .map { (barnId, nyttGrunnlag) ->
        val aktivtGrunnlag = aktivBoforholdData.filter { it.gjelderPersonId == barnId }
        if (aktivtGrunnlag.erDetSammeSom(nyttGrunnlag, virkniningstidspunkt)) return@map null
        nyttGrunnlag.tilHusstandsmedlemGrunnlagDto(barnId, innhentetTidspunkt)
    }.filterNotNull()
    .toSet()

private fun List<BoforholdResponseV2>.tilHusstandsmedlemGrunnlagDto(
    barnId: String?,
    innhentetTidspunkt: LocalDateTime,
) = HusstandsmedlemGrunnlagDto(
    map {
        HusstandsmedlemGrunnlagDto.BostatusperiodeGrunnlagDto(
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
        val aktivtSivilstandsgrunnlag =
            aktiveGrunnlag.find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val nyttBearbeidaSivilstandsgrunnlag = find { it.type == Grunnlagsdatatype.SIVILSTAND && it.erBearbeidet }
        val nyttSivilstandsgrunnlag = find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }
        val aktiveSivilstandsdata =
            aktivtSivilstandsgrunnlag
                .konvertereData<List<Sivilstand>>()
                ?.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkniningstidspunkt)
        val nyeSivilstandsdata =
            nyttBearbeidaSivilstandsgrunnlag
                .konvertereData<List<Sivilstand>>()
                ?.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkniningstidspunkt)
        if (aktiveSivilstandsdata != null &&
            nyeSivilstandsdata != null &&
            !nyeSivilstandsdata.erDetSammeSom(
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
                innhentetTidspunkt = nyttBearbeidaSivilstandsgrunnlag!!.innhentet,
                grunnlag =
                    nyttSivilstandsgrunnlag
                        ?.konvertereData<List<SivilstandGrunnlagDto>>()
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

fun List<Sivilstand>.erDetSammeSom(other: List<Sivilstand>): Boolean {
    if (size != other.size) return false
    return sortedBy { it.periodeFom }.all { sivilstand ->
        other.sortedBy { it.periodeFom }.any {
            it.periodeFom == sivilstand.periodeFom &&
                it.periodeTom == sivilstand.periodeTom &&
                it.sivilstandskode == sivilstand.sivilstandskode
        }
    }
}

fun Set<ArbeidsforholdGrunnlagDto>.erDetSammeSom(settB: Set<ArbeidsforholdGrunnlagDto>): Boolean {
    if (this.size != settB.size) return false

    return this.all { settA ->
        settB.any { settB ->
            val detaljerA = settA.ansettelsesdetaljerListe?.get(0)
            val detaljerB = settB.ansettelsesdetaljerListe?.get(0)

            settB.partPersonId == settA.partPersonId &&
                settB.startdato == settA.startdato &&
                settB.arbeidsgiverNavn == settA.arbeidsgiverNavn &&
                detaljerB?.periodeFra == detaljerA?.periodeFra &&
                detaljerB?.periodeTil == detaljerA?.periodeTil &&
                detaljerB?.avtaltStillingsprosent == detaljerA?.avtaltStillingsprosent &&
                detaljerB?.sisteLønnsendringDato == detaljerA?.sisteLønnsendringDato
        }
    }
}

fun Set<BarnetilsynGrunnlagDto>.erLik(
    detAndreSettet: Set<BarnetilsynGrunnlagDto>,
    virkningsdato: LocalDate,
): Boolean {
    if (this.size != detAndreSettet.size) return false

    fun BarnetilsynGrunnlagDto.justereFradato() =
        if (virkningsdato.isAfter(LocalDate.now())) {
            maxOf(virkningsdato.withDayOfMonth(1))
        } else {
            maxOf(virkningsdato.withDayOfMonth(1), periodeFra)
        }
    return this.all { barnetilsyn ->
        detAndreSettet.any {
            it.justereFradato() == barnetilsyn.justereFradato() &&
                it.periodeTil == barnetilsyn.periodeTil &&
                it.barnPersonId == barnetilsyn.barnPersonId &&
                it.skolealder == barnetilsyn.skolealder &&
                it.tilsynstype == barnetilsyn.tilsynstype
        }
    }
}

fun List<BoforholdResponseV2>.erDetSammeSom(
    other: List<BoforholdResponseV2>,
    virkniningstidspunkt: LocalDate,
): Boolean {
    if (this.size != other.size) return false

    fun BoforholdResponseV2.justertDatoFom() =
        if (virkniningstidspunkt.isAfter(LocalDate.now())) {
            maxOf(fødselsdato, virkniningstidspunkt.withDayOfMonth(1))
        } else {
            maxOf(virkniningstidspunkt.withDayOfMonth(1), periodeFom)
        }
    return this.all { boforhold ->
        other.any {
            it.justertDatoFom() == boforhold.justertDatoFom() &&
                it.periodeTom == boforhold.periodeTom &&
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

    val oppdatertGrunnlag = henteBearbeidaInntekterForType(type, rolle.ident!!)
    val innhentetTidspunkt = find { it.type == type && it.erBearbeidet }?.innhentet ?: LocalDateTime.now()
    val oppdaterteEllerNyeInntekter =
        oppdatertGrunnlag
            ?.inntekter
            ?.map { grunnlag ->
                val eksisterendeInntekt =
                    inntekterRolle.find {
                        it.kilde == Kilde.OFFENTLIG &&
                            it.erDetSammeSom(grunnlag)
                    }
                        ?: return@map grunnlag.tilIkkeAktivInntektDto(
                            rolle.ident!!,
                            GrunnlagInntektEndringstype.NY,
                            innhentetTidspunkt,
                        )
                val erBeløpEndret =
                    eksisterendeInntekt.belop.nærmesteHeltall != grunnlag.sumInntekt.nærmesteHeltall
                val er12MndOg3MndPeriodeEndret =
                    eksisterendeInntekt.opprinneligPeriode != null &&
                        ainntekt12Og3Måneder.contains(eksisterendeInntekt.type) &&
                        eksisterendeInntekt.opprinneligPeriode != grunnlag.periode
                if (erBeløpEndret ||
                    er12MndOg3MndPeriodeEndret ||
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

    val slettedeInntekter =
        inntekterRolle
            .filter { it.kilde == Kilde.OFFENTLIG && type.inneholder(it.type) }
            .filter { inntekt ->
                oppdatertGrunnlag
                    ?.inntekter
                    ?.none { inntekt.erDetSammeSom(it) } == true
            }.map { it.tilIkkeAktivInntektDto(GrunnlagInntektEndringstype.SLETTET, LocalDateTime.now()) }

    return oppdaterteEllerNyeInntekter
        .filter { it.endringstype != GrunnlagInntektEndringstype.INGEN_ENDRING }
        .toSet() + slettedeInntekter
}

fun Grunnlagsdatatype.inneholder(type: Inntektsrapportering) =
    when (this) {
        Grunnlagsdatatype.KONTANTSTØTTE -> type == Inntektsrapportering.KONTANTSTØTTE
        Grunnlagsdatatype.BARNETILLEGG -> type == Inntektsrapportering.BARNETILLEGG
        Grunnlagsdatatype.SMÅBARNSTILLEGG -> type == Inntektsrapportering.SMÅBARNSTILLEGG
        Grunnlagsdatatype.UTVIDET_BARNETRYGD -> type == Inntektsrapportering.UTVIDET_BARNETRYGD
        else -> !eksplisitteYtelser.contains(type)
    }

fun Inntekt.erDetSammeSom(grunnlag: SummertÅrsinntekt): Boolean {
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

fun Behandling.henteUaktiverteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolle: Rolle,
): Set<Grunnlag> =
    grunnlag
        .hentAlleIkkeAktiv()
        .filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()

fun Behandling.henteAktiverteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolle: Rolle,
): Set<Grunnlag> =
    grunnlag
        .hentAlleAktiv()
        .filter {
            it.type == grunnlagstype.type && it.rolle.id == rolle.id && grunnlagstype.erBearbeidet == it.erBearbeidet
        }.toSet()
