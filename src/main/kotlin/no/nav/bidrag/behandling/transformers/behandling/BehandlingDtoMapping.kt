package no.nav.bidrag.behandling.transformers.behandling

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsbarnGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandAktivGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærtilskuddUtgifterDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarnperiodeDto
import no.nav.bidrag.behandling.transformers.ekskluderYtelserFørVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.erSærligeUtgifter
import no.nav.bidrag.behandling.transformers.finnCutoffDatoFom
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioder
import no.nav.bidrag.behandling.transformers.inntekstrapporteringerSomKreverGjelderBarn
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.sorter
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.sortert
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validereSivilstand
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.FeilrapporteringDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import java.time.LocalDate
import java.time.ZoneOffset

// TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
@Suppress("ktlint:standard:value-argument-comment")
fun Behandling.tilBehandlingDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag>,
    ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata? = null,
    inkluderHistoriskeInntekter: Boolean = false,
) = BehandlingDtoV2(
    id = id!!,
    type = tilType(),
    vedtakstype = vedtakstype,
    stønadstype = stonadstype,
    engangsbeløptype = engangsbeloptype,
    erKlageEllerOmgjøring = erKlageEllerOmgjøring,
    opprettetTidspunkt = opprettetTidspunkt,
    erVedtakFattet = vedtaksid != null,
    søktFomDato = søktFomDato,
    mottattdato = mottattdato,
    søktAv = soknadFra,
    saksnummer = saksnummer,
    søknadsid = soknadsid,
    behandlerenhet = behandlerEnhet,
    roller =
        roller.map {
            RolleDto(
                it.id!!,
                it.rolletype,
                it.ident,
                it.navn ?: hentPersonVisningsnavn(it.ident),
                it.foedselsdato,
            )
        }.toSet(),
    søknadRefId = soknadRefId,
    vedtakRefId = refVedtaksid,
    virkningstidspunkt =
        VirkningstidspunktDto(
            virkningstidspunkt = virkningstidspunkt,
            opprinneligVirkningstidspunkt = opprinneligVirkningstidspunkt,
            årsak = årsak,
            avslag = avslag,
            notat =
                BehandlingNotatDto(
                    medIVedtaket = virkningstidspunktsbegrunnelseIVedtakOgNotat,
                    kunINotat = virkningstidspunktbegrunnelseKunINotat,
                ),
        ),
    boforhold = tilBoforholdV2(),
    inntekter =
        tilInntektDtoV2(
            gjeldendeAktiveGrunnlagsdata,
            inkluderHistoriskeInntekter = inkluderHistoriskeInntekter,
        ),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.tilAktivGrunnlagsdata(),
    utgift =
        utgift?.let { utgift ->
            SærtilskuddUtgifterDto(
                beregning = utgift.tilBeregningDto(),
                notat =
                    BehandlingNotatDto(
                        kunINotat = utgiftsbegrunnelseKunINotat,
                    ),
                utgifter = utgift.utgiftsposter.sorter().map { it.tilDto() },
            )
        } ?: if (erSærligeUtgifter()) {
            SærtilskuddUtgifterDto(
                notat =
                    BehandlingNotatDto(
                        kunINotat = utgiftsbegrunnelseKunINotat,
                    ),
            )
        } else {
            null
        },
    ikkeAktiverteEndringerIGrunnlagsdata =
        ikkeAktiverteEndringerIGrunnlagsdata
            ?: IkkeAktiveGrunnlagsdata(),
    feilOppståttVedSisteGrunnlagsinnhenting =
        grunnlagsinnhentingFeilet?.let {
            val typeRef: TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>> =
                object : TypeReference<Map<Grunnlagsdatatype, FeilrapporteringDto>>() {}

            objectmapper.readValue(it, typeRef).tilGrunnlagsinnhentingsfeil(this)
        },
)

private fun Map<Grunnlagsdatatype, FeilrapporteringDto>.tilGrunnlagsinnhentingsfeil(behandling: Behandling) =
    this.map { feil ->
        Grunnlagsinnhentingsfeil(
            rolleid = behandling.roller.find { feil.value.personId == it.ident }!!.id!!,
            feilmelding = feil.value.feilmelding ?: "Uspesifisert feil oppstod ved innhenting av grunnlag",
            grunnlagsdatatype = feil.key,
            periode = feil.value.periodeFra?.let { Datoperiode(feil.value.periodeFra!!, feil.value.periodeTil) },
        )
    }.toSet()

fun Grunnlag?.toSivilstand(): SivilstandAktivGrunnlagDto? {
    if (this == null) return null
    val harUgyldigStatus =
        behandling.sivilstand.any { it.sivilstand == Sivilstandskode.UKJENT } || behandling.sivilstand.isEmpty()
    val sortertGrunnlag = konvertereData<List<SivilstandGrunnlagDto>>()?.sortedBy { it.gyldigFom }
    val filtrertGrunnlag =
        harUgyldigStatus.ifTrue { sortertGrunnlag }
            ?: sortertGrunnlag?.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)

    return SivilstandAktivGrunnlagDto(
        grunnlag = filtrertGrunnlag?.toSet() ?: emptySet(),
        innhentetTidspunkt = innhentet,
    )
}

fun List<Grunnlag>.tilHusstandsbarn() =
    this.map {
        HusstandsbarnGrunnlagDto(
            innhentetTidspunkt = it.innhentet,
            ident = it.gjelder,
            perioder =
                it.konvertereData<List<BoforholdResponse>>()?.map { boforholdrespons ->
                    HusstandsbarnGrunnlagDto.HusstandsbarnGrunnlagPeriodeDto(
                        boforholdrespons.periodeFom,
                        boforholdrespons.periodeTom,
                        boforholdrespons.bostatus,
                    )
                }?.toSet() ?: emptySet(),
        )
    }.toSet()

fun Behandling.tilBoforholdV2() =
    BoforholdDtoV2(
        husstandsbarn = husstandsbarn.sortert().map { it.tilHusstandsbarnperiodeDto() }.toSet(),
        sivilstand = sivilstand.toSivilstandDto(),
        notat =
            BehandlingNotatDto(
                medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                kunINotat = boforholdsbegrunnelseKunINotat,
            ),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn = husstandsbarn.validerBoforhold(virkningstidspunktEllerSøktFomDato).filter { it.harFeil },
                sivilstand = sivilstand.validereSivilstand(virkningstidspunktEllerSøktFomDato).takeIf { it.harFeil },
            ),
    )

fun Behandling.tilInntektDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag> = emptyList(),
    inkluderHistoriskeInntekter: Boolean = false,
) = InntekterDtoV2(
    barnetillegg =
        inntekter.filter { it.type == Inntektsrapportering.BARNETILLEGG }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2().toSet(),
    utvidetBarnetrygd =
        inntekter.filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    kontantstøtte =
        inntekter.filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2().toSet(),
    småbarnstillegg =
        inntekter.filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2().toSet(),
    månedsinntekter =
        gjeldendeAktiveGrunnlagsdata.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
            .flatMap { grunnlag ->
                grunnlag.konvertereData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                    it.tilInntektDtoV2(
                        grunnlag.rolle.ident!!,
                    )
                } ?: emptyList()
            }.toSet(),
    årsinntekter =
        inntekter.årsinntekterSortert(inkluderHistoriskeInntekter = inkluderHistoriskeInntekter)
            .tilInntektDtoV2()
            .toSet(),
    beregnetInntekter = hentBeregnetInntekter(),
    notat =
        BehandlingNotatDto(
            medIVedtaket = inntektsbegrunnelseIVedtakOgNotat,
            kunINotat = inntektsbegrunnelseKunINotat,
        ),
    valideringsfeil = hentInntekterValideringsfeil(),
)

fun List<Grunnlag>.tilAktivGrunnlagsdata() =
    AktiveGrunnlagsdata(
        arbeidsforhold =
            find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }.konvertereData<Set<ArbeidsforholdGrunnlagDto>>()
                ?: emptySet(),
        husstandsbarn =
            filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }.tilHusstandsbarn(),
        sivilstand =
            find { it.type == Grunnlagsdatatype.SIVILSTAND && !it.erBearbeidet }.toSivilstand(),
    )

fun Behandling.hentInntekterValideringsfeil(): InntektValideringsfeilDto {
    return InntektValideringsfeilDto(
        årsinntekter =
            inntekter.mapValideringsfeilForÅrsinntekter(
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
        barnetillegg =
            inntekter.mapValideringsfeilForYtelseSomGjelderBarn(
                Inntektsrapportering.BARNETILLEGG,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
        småbarnstillegg =
            inntekter.mapValideringsfeilForYtelse(
                Inntektsrapportering.SMÅBARNSTILLEGG,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ),
        utvidetBarnetrygd =
            inntekter.mapValideringsfeilForYtelse(
                Inntektsrapportering.UTVIDET_BARNETRYGD,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ),
        kontantstøtte =
            inntekter.mapValideringsfeilForYtelseSomGjelderBarn(
                Inntektsrapportering.KONTANTSTØTTE,
                virkningstidspunktEllerSøktFomDato,
                roller,
            ).takeIf { it.isNotEmpty() },
    )
}

fun Set<Inntekt>.mapValideringsfeilForÅrsinntekter(
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
): Set<InntektValideringsfeil> {
    val inntekterSomSkalSjekkes = filter { !eksplisitteYtelser.contains(it.type) }.filter { it.taMed }
    return roller.map { rolle ->
        val inntekterTaMed = inntekterSomSkalSjekkes.filter { it.ident == rolle.ident }
        if (inntekterTaMed.isEmpty() && (rolle.rolletype == Rolletype.BIDRAGSMOTTAKER || rolle.rolletype == Rolletype.BIDRAGSPLIKTIG)) {
            InntektValideringsfeil(
                hullIPerioder = emptyList(),
                overlappendePerioder = emptySet(),
                fremtidigPeriode = false,
                manglerPerioder = true,
                ident = rolle.ident!!,
                rolle = rolle.rolletype,
            )
        } else {
            InntektValideringsfeil(
                hullIPerioder = inntekterTaMed.finnHullIPerioder(virkningstidspunkt),
                overlappendePerioder = inntekterTaMed.finnOverlappendePerioder(),
                fremtidigPeriode = inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
                manglerPerioder =
                    (rolle.rolletype != Rolletype.BARN)
                        .ifTrue { this.isEmpty() } ?: false,
                ident = rolle.ident!!,
                rolle = rolle.rolletype,
            )
        }
    }.filter { it.harFeil }.toSet()
}

fun Set<Inntekt>.mapValideringsfeilForYtelse(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    gjelderBarn: String? = null,
) = filter { it.taMed }.filter { it.type == type }.let { inntekterTaMed ->
    val inntektGjelderIdent = inntekterTaMed.firstOrNull()?.ident
    val gjelderRolle = roller.find { it.ident == inntektGjelderIdent }
    val gjelderIdent = gjelderRolle?.ident ?: inntektGjelderIdent ?: ""
    InntektValideringsfeil(
        overlappendePerioder = inntekterTaMed.finnOverlappendePerioder(),
        fremtidigPeriode =
            inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
        ident = gjelderIdent,
        rolle = gjelderRolle?.rolletype,
        gjelderBarn = gjelderBarn,
        erYtelse = true,
    ).takeIf { it.harFeil }
}

fun Set<Inntekt>.mapValideringsfeilForYtelseSomGjelderBarn(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
) = filter { inntekstrapporteringerSomKreverGjelderBarn.contains(type) }
    .groupBy { it.gjelderBarn }.map { (gjelderBarn, inntekter) ->
        inntekter.toSet().mapValideringsfeilForYtelse(
            type,
            virkningstidspunkt,
            roller,
            gjelderBarn,
        )
    }.filterNotNull().toSet()

fun List<Inntekt>.inneholderFremtidigPeriode(virkningstidspunkt: LocalDate) =
    any {
        it.datoFom!!.isAfter(maxOf(virkningstidspunkt.withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
    }

fun Behandling.hentBeregnetInntekter() =
    BeregnApi().beregnInntekt(tilInntektberegningDto()).inntektPerBarnListe.sortedBy {
        it.inntektGjelderBarnIdent?.verdi
    }.map {
        it.copy(
            summertInntektListe =
                it.summertInntektListe.map { delberegning ->
                    delberegning.copy(
                        barnetillegg = delberegning.barnetillegg?.nærmesteHeltall,
                        småbarnstillegg = delberegning.småbarnstillegg?.nærmesteHeltall,
                        kontantstøtte = delberegning.kontantstøtte?.nærmesteHeltall,
                        utvidetBarnetrygd = delberegning.utvidetBarnetrygd?.nærmesteHeltall,
                        skattepliktigInntekt = delberegning.skattepliktigInntekt?.nærmesteHeltall,
                        totalinntekt = delberegning.totalinntekt.nærmesteHeltall,
                    )
                },
        )
    }

fun Behandling.tilReferanseId() = "bidrag_behandling_${id}_${opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)}"

fun Behandling.notatTittel(): String {
    val prefiks =
        when (stonadstype) {
            Stønadstype.FORSKUDD -> "Bidragsforskudd"
            Stønadstype.BIDRAG -> "Barnebidrag"
            Stønadstype.BIDRAG18AAR -> "Barnebidrag 18 år"
            Stønadstype.EKTEFELLEBIDRAG -> "Ektefellebidrag"
            Stønadstype.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
            Stønadstype.MOTREGNING -> "Motregning"
            else ->
                when (engangsbeloptype) {
                    Engangsbeløptype.ETTERGIVELSE -> "Ettergivelse"
                    Engangsbeløptype.ETTERGIVELSE_TILBAKEKREVING -> "Ettergivelse tilbakekreving"
                    Engangsbeløptype.GEBYR_MOTTAKER -> "Gebyr"
                    Engangsbeløptype.GEBYR_SKYLDNER -> "Gebyr"
                    Engangsbeløptype.TILBAKEKREVING -> "Tilbakekreving"
                    else -> null
                }
        }
    return "${prefiks?.let { "$prefiks, " }}Saksbehandlingsnotat"
}

fun List<BoforholdResponse>.filtrerPerioderEtterVirkningstidspunkt(
    husstandsbarnListe: Set<Husstandsbarn>,
    virkningstidspunkt: LocalDate,
): List<BoforholdResponse> {
    return groupBy { it.relatertPersonPersonId }.flatMap { (barnId, perioder) ->
        val barn =
            husstandsbarnListe.find { it.ident == barnId }
                ?: return@flatMap perioder
        val perioderFiltrert =
            perioder.sortedBy { it.periodeFom }.slice(
                perioder.map { it.periodeFom }
                    .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, barn.fødselsdato),
            )
        val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, barn.fødselsdato)
        perioderFiltrert.map { periode ->
            periode.takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }
}

fun List<SivilstandGrunnlagDto>.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
): List<SivilstandGrunnlagDto> {
    val filtrertGrunnlag =
        sortedBy { it.gyldigFom }.slice(map { it.gyldigFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))
    return filtrertGrunnlag.ifEmpty {
        listOf(sortedBy { it.gyldigFom }.last())
    }
}

fun List<LocalDate?>.hentIndekserEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
    fødselsdato: LocalDate? = null,
): List<Int> {
    val kanIkkeVæreTidligereEnnDato = finnCutoffDatoFom(virkningstidspunkt, fødselsdato)
    val datoerSortert = sortedBy { it }

    return datoerSortert.mapIndexedNotNull { index, dato ->
        index.takeIf {
            if (dato == null) return@takeIf true
            val erEtterVirkningstidspunkt = dato >= kanIkkeVæreTidligereEnnDato
            if (!erEtterVirkningstidspunkt) {
                val nesteDato = datoerSortert.drop(index + 1).firstOrNull()
                nesteDato == null || nesteDato > kanIkkeVæreTidligereEnnDato
            } else {
                true
            }
        }
    }
}

fun SivilstandBeregnet.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(virkningstidspunkt: LocalDate): SivilstandBeregnet {
    return copy(
        sivilstandListe =
            sivilstandListe.sortedBy { it.periodeFom }.slice(
                sivilstandListe.map {
                    it.periodeFom
                }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt),
            ),
    )
}

fun List<Sivilstand>.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkningstidspunkt: LocalDate): List<Sivilstand> {
    return sortedBy { it.periodeFom }.slice(map { it.periodeFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))
}

fun List<Grunnlag>.hentAlleBearbeidaBoforhold(
    virkniningstidspunkt: LocalDate,
    husstandsbarn: Set<Husstandsbarn>,
    rolle: Rolle,
) = asSequence()
    .filter { (it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    .mapNotNull { it.konvertereData<List<BoforholdResponse>>() }
    .flatten().distinct().toList().filtrerPerioderEtterVirkningstidspunkt(
        husstandsbarn,
        virkniningstidspunkt,
    ).sortedBy { it.periodeFom }
