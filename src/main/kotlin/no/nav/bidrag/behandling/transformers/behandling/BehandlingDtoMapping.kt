package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konverterData
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.AktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.HusstandsbarnGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktiveGrunnlagsdata
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandAktivGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.boforhold.tilDto
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.finnCutoffHusstandsmedlemDatoFom
import no.nav.bidrag.behandling.transformers.finnCutoffSivilstandDatoFom
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioder
import no.nav.bidrag.behandling.transformers.inntekstrapporteringerSomKreverGjelderBarn
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.sortert
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.toSivilstandDto
import no.nav.bidrag.behandling.transformers.validerBoforhold
import no.nav.bidrag.behandling.transformers.validerSivilstand
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import java.time.LocalDate
import java.time.ZoneOffset

// TODO: Endre navn til BehandlingDto når v2-migreringen er ferdigstilt
@Suppress("ktlint:standard:value-argument-comment")
fun Behandling.tilBehandlingDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag>,
    ikkeAktiverteEndringerIGrunnlagsdata: IkkeAktiveGrunnlagsdata? = null,
) = BehandlingDtoV2(
    id = id!!,
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
    inntekter = tilInntektDtoV2(gjeldendeAktiveGrunnlagsdata),
    aktiveGrunnlagsdata = gjeldendeAktiveGrunnlagsdata.tilAktivGrunnlagsdata(),
    ikkeAktiverteEndringerIGrunnlagsdata =
        ikkeAktiverteEndringerIGrunnlagsdata
            ?: IkkeAktiveGrunnlagsdata(),
)

fun Grunnlag?.toSivilstand(): SivilstandAktivGrunnlagDto? {
    if (this == null) return null
    val grunnlag =
        konverterData<List<SivilstandGrunnlagDto>>()
            ?.filtrerSivilstandPerioderEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)
    return this?.let {
        SivilstandAktivGrunnlagDto(
            grunnlag = grunnlag?.toSet() ?: emptySet(),
            innhentetTidspunkt = innhentet,
        )
    }
}

fun Grunnlag?.toHusstandsbarn(): Set<HusstandsbarnGrunnlagDto> {
    if (this == null) return emptySet()
    return konverterData<List<BoforholdResponse>>()?.groupBy { it.relatertPersonPersonId }?.map { (barnId, grunnlag) ->
        HusstandsbarnGrunnlagDto(
            innhentetTidspunkt = this.innhentet,
            ident = barnId,
            perioder =
                grunnlag.filtrerPerioderEtterVirkningstidspunkt(
                    behandling.husstandsbarn,
                    behandling.virkningstidspunktEllerSøktFomDato,
                ).map {
                    HusstandsbarnGrunnlagDto.HusstandsbarnGrunnlagPeriodeDto(
                        it.periodeFom,
                        it.periodeTom,
                        it.bostatus,
                    )
                }.toSet(),
        )
    }?.toSet() ?: emptySet()
}

fun Behandling.tilBoforholdV2() =
    BoforholdDtoV2(
        husstandsbarn = husstandsbarn.sortert().map { it.tilDto() }.toSet(),
        sivilstand = sivilstand.toSivilstandDto(),
        notat =
            BehandlingNotatDto(
                medIVedtaket = boforholdsbegrunnelseIVedtakOgNotat,
                kunINotat = boforholdsbegrunnelseKunINotat,
            ),
        valideringsfeil =
            BoforholdValideringsfeil(
                husstandsbarn = husstandsbarn.validerBoforhold(virkningstidspunktEllerSøktFomDato).filter { it.harFeil },
                sivilstand = sivilstand.validerSivilstand(virkningstidspunktEllerSøktFomDato).takeIf { it.harFeil },
            ),
    )

fun Behandling.tilInntektDtoV2(gjeldendeAktiveGrunnlagsdata: List<Grunnlag> = emptyList()) =
    InntekterDtoV2(
        barnetillegg =
            inntekter.filter { it.type == Inntektsrapportering.BARNETILLEGG }
                .sorterEtterDatoOgBarn()
                .tilInntektDtoV2().toSet(),
        utvidetBarnetrygd =
            inntekter.filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
                .sorterEtterDato()
                .tilInntektDtoV2()
                .toSet(),
        kontantstøtte =
            inntekter.filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
                .sorterEtterDatoOgBarn()
                .tilInntektDtoV2().toSet(),
        småbarnstillegg =
            inntekter.filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
                .sorterEtterDato()
                .tilInntektDtoV2().toSet(),
        månedsinntekter =
            gjeldendeAktiveGrunnlagsdata.filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
                .flatMap { grunnlag ->
                    grunnlag.konverterData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                        it.tilInntektDtoV2(
                            grunnlag.rolle.ident!!,
                        )
                    } ?: emptyList()
                }.toSet(),
        årsinntekter =
            inntekter.årsinntekterSortert()
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
            find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD && !it.erBearbeidet }.konverterData<Set<ArbeidsforholdGrunnlagDto>>()
                ?: emptySet(),
        husstandsbarn =
            find { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }.toHusstandsbarn(),
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
        val cutoffPeriodeFom = finnCutoffHusstandsmedlemDatoFom(virkningstidspunkt, barn.fødselsdato)

        val perioderSorted = perioder.sortedBy { it.periodeFom }
        val perioderFiltrert =
            perioderSorted.filterIndexed { index, periode ->
                val erEtterVirkningstidspunkt = periode.periodeFom >= cutoffPeriodeFom
                if (!erEtterVirkningstidspunkt) {
                    val nestePeriode = perioderSorted.drop(index + 1).firstOrNull()
                    nestePeriode?.periodeFom == null || nestePeriode.periodeFom > cutoffPeriodeFom
                } else {
                    true
                }
            }
        perioderFiltrert.map { periode ->
            periode.takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }
}

fun List<SivilstandGrunnlagDto>.filtrerSivilstandPerioderEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
): List<SivilstandGrunnlagDto> {
    val kanIkkeVæreSenereEnnDato = finnCutoffSivilstandDatoFom(virkningstidspunkt)
    val sivilstandSortert = sortedBy { it.gyldigFom }

    return filterIndexed { index, periode ->
        val erEtterVirkningstidspunkt = periode.gyldigFom != null && periode.gyldigFom!! >= kanIkkeVæreSenereEnnDato
        if (!erEtterVirkningstidspunkt) {
            val nestePeriode = sivilstandSortert.drop(index + 1).firstOrNull()
            nestePeriode?.gyldigFom == null || nestePeriode.gyldigFom!! > kanIkkeVæreSenereEnnDato
        } else {
            true
        }
    }
}

fun List<Grunnlag>.hentAlleBearbeidetBoforhold(
    virkniningstidspunkt: LocalDate,
    husstandsbarn: Set<Husstandsbarn>,
    rolle: Rolle?,
) = asSequence()
    .filter { (rolle == null || it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    .mapNotNull { it.konverterData<List<BoforholdResponse>>() }
    .flatten().distinct().toList().filtrerPerioderEtterVirkningstidspunkt(
        husstandsbarn,
        virkniningstidspunkt,
    )
