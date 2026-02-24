package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.dto.v1.beregning.BidragPeriodeBeregningsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragsevneDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelberegningBidragspliktigesBeregnedeTotalbidragDto
import no.nav.bidrag.behandling.dto.v1.beregning.DelvedtakDto
import no.nav.bidrag.behandling.dto.v1.beregning.ForholdsmessigFordelingBeregningsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.ForholdsmessigFordelingBidragTilFordelingBarn
import no.nav.bidrag.behandling.dto.v1.beregning.IndeksreguleringDetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.KlageOmgjøringDetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.PeriodeSlåttUtTilFF
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBarnebidragsberegningPeriodeDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningInntekterDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegning
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v1.beregning.SluttberegningBarnebidrag2
import no.nav.bidrag.behandling.dto.v1.beregning.UgyldigBeregningDto
import no.nav.bidrag.behandling.dto.v1.beregning.finnSluttberegningIReferanser
import no.nav.bidrag.behandling.dto.v2.behandling.GebyrDetaljerDto
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto
import no.nav.bidrag.behandling.dto.v2.underhold.UnderholdskostnadDto.UnderholdskostnadPeriodeBeregningsdetaljer
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.service.hentVedtak
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.behandling.tilSøknadsdetaljerDto
import no.nav.bidrag.behandling.transformers.forholdsmessigfordeling.erForholdsmessigFordeling
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.inntekt.bestemDatoTomForOffentligInntekt
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.hentBehandlingDetaljer
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.hentSøknader
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BeregnGebyrResultat
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnInnkrevesFraDato
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erAvslag
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erAvvisning
import no.nav.bidrag.domene.enums.beregning.Resultatkode.Companion.erDirekteAvslag
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorRequestV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorResponse
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningResultatBarnV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtak
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtakV2
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnValgteInntekterGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.InntektsgrunnlagPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.behandling.felles.grunnlag.AldersjusteringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningAndelAvBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnetilleggSkattesats
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragTilFordeling
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragTilFordelingLøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragTilFordelingPrivatAvtale
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBidragspliktigesBeregnedeTotalbidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningEndringSjekkGrensePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningEvne25ProsentAvInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningFaktiskTilsynsutgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningIndeksreguleringPrivatAvtale
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningNettoBarnetillegg
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningNettoTilsynsutgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningPrivatAvtale
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsfradrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSamværsklasse
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumBidragTilFordeling
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningTilleggsstønad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUnderholdskostnad
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningVoksneIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InnholdMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.KopiDelberegningBidragspliktigesAndel
import no.nav.bidrag.transport.behandling.felles.grunnlag.KopiSamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtalePeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsklassePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonBidragsevnePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonMaksFradragPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonMaksTilsynPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SjablonSjablontallPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningBarnebidragAldersjustering
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningIndeksregulering
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilleggsstønadPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.TilsynsutgiftBarn
import no.nav.bidrag.transport.behandling.felles.grunnlag.ValutakursGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottakerReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.byggSluttberegningBarnebidragDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.erResultatEndringUnderGrense
import no.nav.bidrag.transport.behandling.felles.grunnlag.erResultatEndringUnderGrenseForPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.erSluttberegningGammelStruktur
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanser
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnDelberegningSjekkGrensePeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertAv
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.sluttberegningPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.vedtak.response.erIndeksEllerAldersjustering
import no.nav.bidrag.transport.behandling.vedtak.response.finnResultatFraAnnenVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.finnSøknadGrunnlag
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toLocalDate
import no.nav.bidrag.transport.felles.toYearMonth
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatBeregning as ResultatBeregningBB
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode as ResultatPeriodeBB

val ikkeBeregnForBarnetillegg = listOf(Inntektstype.BARNETILLEGG_TILTAKSPENGER, Inntektstype.BARNETILLEGG_SUMMERT)

fun Rolle.mapTilResultatBarn() =
    ResultatRolle(tilPersonident(), hentNavn(), fødselsdato, innbetaltBeløp, tilGrunnlagsreferanse(), stønadstype, grunnlagFraVedtakListe)

fun Rolle.tilPersonident() = ident?.let { Personident(it) }

fun mapTilBeregningresultatBarn(
    søknadsbarn: Rolle,
    erAvvistRevurdering: Boolean,
    resultatBarn: BidragsberegningResultatBarnV2,
    grunnlagBarn: List<GrunnlagDto>,
    grunnlagBeregning: BidragsberegningOrkestratorRequestV2,
    behandling: Behandling,
    endeligResultat: ResultatVedtakV2?,
): ResultatBidragsberegningBarn =
    ResultatBidragsberegningBarn(
        barn = søknadsbarn.mapTilResultatBarn(),
        erAvvistRevurdering = erAvvistRevurdering,
        medInnkreving = (søknadsbarn.innkrevingstype ?: behandling.innkrevingstype) == Innkrevingstype.MED_INNKREVING,
        avslagskode = søknadsbarn.avslag,
        resultatVedtak =
            BidragsberegningOrkestratorResponse(
                resultatVedtakListe =
                    if (erAvvistRevurdering && !behandling.erKlageEllerOmgjøring) {
                        emptyList()
                    } else {
                        resultatBarn.resultatVedtakListe.map {
                            ResultatVedtak(
                                vedtakstype = it.vedtakstype,
                                delvedtak = it.delvedtak,
                                omgjøringsvedtak = it.omgjøringsvedtak,
                                beregnet = it.beregnet,
                                beregnetFraDato = it.beregnetFraDato,
                                resultat =
                                    BeregnetBarnebidragResultat(
                                        beregnetBarnebidragPeriodeListe = it.periodeListe,
                                        grunnlagListe =
                                            if (it.omgjøringsvedtak) {
                                                grunnlagBarn + grunnlagBeregning.grunnlagsliste
                                            } else if (it.delvedtak) {
                                                it.grunnlagslisteDelvedtak
                                            } else {
                                                grunnlagBarn
                                            },
                                    ),
                            )
                        }
                    },
            ),
        omgjøringsdetaljer = behandling.omgjøringsdetaljer,
        beregnTilDato =
            behandling
                .finnBeregnTilDatoBehandling(`søknadsbarn`)
                ?.toYearMonth(),
        innkrevesFraDato = behandling.finnInnkrevesFraDato(`søknadsbarn`),
        opphørsdato = `søknadsbarn`.opphørsdato?.toYearMonth(),
        resultat =
            if (endeligResultat != null && søknadsbarn.erDirekteAvslag) {
                val sistePeriode = endeligResultat.periodeListe.maxByOrNull { it.periode.fom }
                val periodeOpphør =
                    sistePeriode?.periode?.til ?: sistePeriode?.periode?.fom ?: søknadsbarn.virkningstidspunktRolle.toYearMonth()
                BeregnetBarnebidragResultat(
                    beregnetBarnebidragPeriodeListe =
                        endeligResultat.periodeListe.ifEmpty {
                            listOf(
                                ResultatPeriodeBB(
                                    periode =
                                        ÅrMånedsperiode(
                                            periodeOpphør,
                                            null,
                                        ),
                                    ResultatBeregningBB(null),
                                    emptyList(),
                                ),
                            )
                        },
                    grunnlagListe = grunnlagBarn + grunnlagBeregning.grunnlagsliste,
                )
            } else if (endeligResultat != null && !erAvvistRevurdering) {
                BeregnetBarnebidragResultat(
                    beregnetBarnebidragPeriodeListe = endeligResultat.periodeListe,
                    grunnlagListe = grunnlagBarn + grunnlagBeregning.grunnlagsliste,
                )
            } else {
                BeregnetBarnebidragResultat()
            },
    )

fun BeregnGebyrResultat.tilDto(
    rolle: Rolle,
    søknadsid: Long,
): GebyrDetaljerDto {
    val saksnummer = rolle.sakForSøknad(søknadsid)
    val gebyr = rolle.hentEllerOpprettGebyr().finnEllerOpprettGebyrForSøknad(søknadsid, saksnummer)
    val erManueltOverstyrt = gebyr.manueltOverstyrtGebyr?.overstyrGebyr == true

    return GebyrDetaljerDto(
        søknad = rolle.tilSøknadsdetaljerDto(søknadsid),
        inntekt =
            GebyrDetaljerDto.GebyrInntektDto(
                skattepliktigInntekt = skattepliktigInntekt,
                maksBarnetillegg = maksBarnetillegg,
            ),
        beregnetIlagtGebyr = ilagtGebyr,
        begrunnelse = if (erManueltOverstyrt) rolle.gebyr?.begrunnelse else null,
        endeligIlagtGebyr =
            if (erManueltOverstyrt) {
                rolle.gebyr!!.ilagtGebyr == true
            } else {
                ilagtGebyr
            },
        beløpGebyrsats = beløpGebyrsats,
        rolle = rolle.tilDto(),
    )
}

fun Behandling.tilInntektberegningDto(rolle: Rolle): BeregnValgteInntekterGrunnlag =
    BeregnValgteInntekterGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunktEllerSøktFomDato,
                finnBeregnTilDatoBehandling(),
            ),
        opphørsdato = rolle.opphørsdatoYearMonth ?: globalOpphørsdatoYearMonth,
        barnIdentListe =
            søknadsbarn
                .filter { it.avslag == null }
                .filter {
                    rolle.rolletype != Rolletype.BIDRAGSMOTTAKER || it.bidragsmottaker?.ident == null ||
                        it.bidragsmottaker?.ident == rolle.ident
                }.map { Personident(it.ident!!) },
        gjelderIdent = Personident(rolle.ident!!),
        grunnlagListe =
            inntekter
                .filter { it.erSammeRolle(rolle) }
                .filter { it.taMed }
                .filter { !it.inntektsposter.mapNotNull { it.inntektstype }.any { ikkeBeregnForBarnetillegg.contains(it) } }
                .map {
                    InntektsgrunnlagPeriode(
                        periode =
                            if (it.kilde == Kilde.OFFENTLIG && eksplisitteYtelser.contains(it.type)) {
                                ÅrMånedsperiode(
                                    maxOf(it.opprinneligFom!!, virkningstidspunktEllerSøktFomDato),
                                    it.bestemDatoTomForOffentligInntekt()?.plusDays(1),
                                )
                            } else {
                                ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusDays(1))
                            },
                        beløp = it.belop,
                        inntektsrapportering = it.type,
                        inntektGjelderBarnIdent = it.gjelderBarnIdent.takeIfNotNullOrEmpty { Personident(it) },
                        inntektEiesAvIdent = Personident(it.gjelderIdent!!),
                    )
                },
    )

fun opprettIndeksreguleringsperioder(
    resultat: ResultatBidragsberegningBarn,
    perioder: List<ResultatBarnebidragsberegningPeriodeDto>,
): List<ResultatBarnebidragsberegningPeriodeDto> {
    if (resultat.resultatVedtak == null) return perioder
    val beregnetIndeksregulering =
        resultat.resultatVedtak.resultatVedtakListe.filter {
            it.beregnet &&
                it.vedtakstype == Vedtakstype.INDEKSREGULERING
        }
    val perioderSomOverlapper =
        beregnetIndeksregulering.filter { pi ->
            perioder.any {
                pi.beregnetFraDato.toYearMonth() == it.periode.fom &&
                    !it.vedtakstype.erIndeksEllerAldersjustering
            }
        }
    if (perioderSomOverlapper.isEmpty()) return perioder
    return (
        perioderSomOverlapper.map { gv ->
            ResultatBarnebidragsberegningPeriodeDto(
                periode = ÅrMånedsperiode(gv.beregnetFraDato, null),
                vedtakstype = gv.vedtakstype,
                resultatKode = Resultatkode.INDEKSREGULERING,
                faktiskBidrag =
                    gv.resultat.beregnetBarnebidragPeriodeListe
                        .first()
                        .resultat.beløp ?: BigDecimal.ZERO,
                aldersjusteringDetaljer = null,
                klageOmgjøringDetaljer =
                    KlageOmgjøringDetaljer(
                        manuellAldersjustering = false,
                        delAvVedtaket = false,
                    ),
            )
        } + perioder
    ).sortedBy { it.periode.fom }
}

fun opprettAldersjusteringPerioder(resultat: ResultatBidragsberegningBarn): List<ResultatBarnebidragsberegningPeriodeDto> {
    if (resultat.resultatVedtak == null) return emptyList()
    return resultat.barn.grunnlagFraVedtak
        .filter {
            (it.vedtak != null || it.grunnlagFraOmgjøringsvedtak) && it.aldersjusteringForÅr != null
        }.mapNotNull { gv ->
            val delvedtakAldersjustering =
                resultat.resultatVedtak.resultatVedtakListe.filter { it.vedtakstype == Vedtakstype.ALDERSJUSTERING }.find {
                    it.beregnetFraDato.year == gv.aldersjusteringForÅr
                }

            if (delvedtakAldersjustering == null) {
                ResultatBarnebidragsberegningPeriodeDto(
                    periode = ÅrMånedsperiode(YearMonth.of(gv.aldersjusteringForÅr!!, 7), null),
                    vedtakstype = Vedtakstype.ALDERSJUSTERING,
                    resultatKode = null,
                    aldersjusteringDetaljer = null,
                    klageOmgjøringDetaljer =
                        KlageOmgjøringDetaljer(
                            manuellAldersjustering = true,
                            delAvVedtaket = false,
                        ),
                )
            } else {
                null
            }
        }
}

fun ResultatBidragsberegning.tilDto(kanFatteVedtakBegrunnelse: String?): ResultatBidragberegningDto =
    ResultatBidragberegningDto(
        kanFatteVedtak = kanFatteVedtakBegrunnelse == null,
        kanFatteVedtakBegrunnelse = kanFatteVedtakBegrunnelse,
        ugyldigBeregning = ugyldigBeregning,
        minstEnPeriodeHarSlåttUtTilFF = grunnlagslisteList.harSlåttUtTilForholdsmessigFordeling(),
        perioderSlåttUtTilFF = grunnlagslisteList.perioderSlåttUtTilFF(),
        resultatBarn =
            resultatBarn
                .parallelStream()
                .map { resultat ->
                    val delvedtakListe = opprettDelvedtak(resultat)
                    val endeligVedtak = delvedtakListe.find { it.endeligVedtak }
                    val grunnlagslisteDelvedtak =
                        resultat.resultatVedtak
                            ?.resultatVedtakListe
                            ?.flatMap {
                                it.resultat.grunnlagListe
                                    .toSet()
                                    .toList()
                            }?.toSet()
                            ?.toList() ?: emptyList()
                    val grunnlagsListe =
                        (resultat.resultat.grunnlagListe.toSet() + grunnlagslisteDelvedtak).toList()
                    val aldersjusteringDetaljer = grunnlagsListe.finnAldersjusteringDetaljerGrunnlag(resultat.barn.referanse)

                    ResultatBidragsberegningBarnDto(
                        barn = resultat.barn,
                        innkrevesFraDato = resultat.innkrevesFraDato,
                        ugyldigBeregning = resultat.ugyldigBeregning,
                        erAvvistRevurdering = resultat.erAvvistRevurdering,
                        medInnkreving = resultat.medInnkreving,
                        erAvvisning = resultat.avslagskode?.erAvvisning() == true,
                        forsendelseDistribueresAutomatisk =
                            vedtakstype == Vedtakstype.ALDERSJUSTERING && aldersjusteringDetaljer?.aldersjustert == true,
                        resultatUtenBeregning =
                            (
                                vedtakstype == Vedtakstype.ALDERSJUSTERING && aldersjusteringDetaljer != null &&
                                    !aldersjusteringDetaljer.aldersjustert
                            ) || vedtakstype == Vedtakstype.INNKREVING,
                        indeksår =
                            if (endeligVedtak != null && resultat.avslagskode?.erDirekteAvslag() == false) {
                                endeligVedtak.indeksår
                            } else if (aldersjusteringDetaljer != null && aldersjusteringDetaljer.aldersjustert) {
                                Year.of(aldersjusteringDetaljer.periode.fom.year).plusYears(1).value
                            } else if (aldersjusteringDetaljer == null && resultat.avslagskode == null) {
                                val sistePeriode =
                                    resultat.resultat.beregnetBarnebidragPeriodeListe
                                        .maxByOrNull { it.periode.fom }
                                grunnlagsListe.finnIndeksår(
                                    resultat.barn.referanse,
                                    sistePeriode?.periode ?: ÅrMånedsperiode(YearMonth.now(), null),
                                    sistePeriode?.grunnlagsreferanseListe ?: emptyList(),
                                )
                            } else {
                                null
                            },
                        delvedtak = delvedtakListe,
                        perioder =
                            if (aldersjusteringDetaljer != null && !aldersjusteringDetaljer.aldersjustert) {
                                listOf(
                                    ResultatBarnebidragsberegningPeriodeDto(
                                        periode = aldersjusteringDetaljer.periode,
                                        vedtakstype = Vedtakstype.ALDERSJUSTERING,
                                        resultatKode = null,
                                        aldersjusteringDetaljer = aldersjusteringDetaljer,
                                    ),
                                )
                            } else {
                                resultat.resultat.beregnetBarnebidragPeriodeListe
                                    .parallelStream()
                                    .map {
                                        val avslagskode = if (it.resultat.beløp == null) resultat.avslagskode else null
                                        grunnlagsListe.byggResultatBidragsberegning(
                                            it.periode,
                                            it.resultat.beløp,
                                            avslagskode,
                                            it.grunnlagsreferanseListe,
                                            resultat.ugyldigBeregning,
                                            grunnlagsListe.erResultatEndringUnderGrense(resultat.barn.referanse),
                                            vedtakstype,
                                            resultat.barn.ident!!,
                                        )
                                    }.toList()
                                    .sortedBy { it.periode.fom }
                            },
                    )
                }.toList()
                .sortedBy { it.barn.fødselsdato },
    )

private fun opprettDelvedtak(resultat: ResultatBidragsberegningBarn): List<DelvedtakDto> =
    resultat.resultatVedtak
        ?.resultatVedtakListe
        ?.parallelStream()
        ?.map { rv ->
            val erEndeligVedtak = !rv.delvedtak && !rv.omgjøringsvedtak
            val grunnlagslisteRV =
                rv.resultat.grunnlagListe
                    .toSet()
                    .toList()
            val aldersjusteringDetaljer = grunnlagslisteRV.finnAldersjusteringDetaljerGrunnlag(resultat.barn.referanse)
            val resultatFraVedtak =
                if (rv.delvedtak) {
                    grunnlagslisteRV.finnResultatFraAnnenVedtak(
                        finnFørsteTreff = true,
                    )
                } else {
                    null
                }

            fun opprettPerioder(): List<ResultatBarnebidragsberegningPeriodeDto> =
                if (aldersjusteringDetaljer != null && !aldersjusteringDetaljer.aldersjustert && rv.delvedtak) {
                    listOf(
                        ResultatBarnebidragsberegningPeriodeDto(
                            periode = aldersjusteringDetaljer.periode,
                            vedtakstype = Vedtakstype.ALDERSJUSTERING,
                            resultatKode = null,
                            aldersjusteringDetaljer = aldersjusteringDetaljer,
                        ),
                    )
                } else if (rv.vedtakstype == Vedtakstype.INDEKSREGULERING) {
                    listOf(
                        ResultatBarnebidragsberegningPeriodeDto(
                            periode =
                                rv.resultat.beregnetBarnebidragPeriodeListe
                                    .first()
                                    .periode,
                            vedtakstype = Vedtakstype.INDEKSREGULERING,
                            resultatKode = null,
                            faktiskBidrag =
                                rv.resultat.beregnetBarnebidragPeriodeListe
                                    .first()
                                    .resultat.beløp!!,
                            beregnetBidrag =
                                rv.resultat.beregnetBarnebidragPeriodeListe
                                    .first()
                                    .resultat.beløp!!,
                        ),
                    )
                } else {
                    rv.resultat.beregnetBarnebidragPeriodeListe
                        .parallelStream()
                        .map { p ->
                            val delvedtak =
                                if (erEndeligVedtak) {
                                    resultat.resultatVedtak.resultatVedtakListe.find {
                                        it.resultat.beregnetBarnebidragPeriodeListe.any {
                                            it.periode.fom ==
                                                p.periode.fom && it.grunnlagsreferanseListe.toSet() == p.grunnlagsreferanseListe.toSet()
                                        }
                                    }
                                } else {
                                    null
                                }

                            val resultatFraVedtak =
                                grunnlagslisteRV.finnResultatFraAnnenVedtak(
                                    p.grunnlagsreferanseListe,
                                )
                            val klagevedtak = resultat.resultatVedtak.resultatVedtakListe.find { it.omgjøringsvedtak }
                            val erKlagevedtak =
                                resultatFraVedtak?.vedtaksid == null &&
                                    klagevedtak?.resultat?.beregnetBarnebidragPeriodeListe?.any {
                                        it.periode.fom == p.periode.fom ||
                                            (p.periode.fom == resultat.opphørsdato && p.resultat.beløp == null)
                                    } == true

                            val aldersjusteringsdetaljer =
                                delvedtak
                                    ?.resultat
                                    ?.grunnlagListe
                                    ?.finnAldersjusteringDetaljerGrunnlag(resultat.barn.referanse)

                            if (aldersjusteringsdetaljer != null && aldersjusteringsdetaljer.aldersjusteresManuelt) {
                                ResultatBarnebidragsberegningPeriodeDto(
                                    periode = p.periode,
                                    vedtakstype = Vedtakstype.ALDERSJUSTERING,
                                    resultatKode = null,
                                    aldersjusteringDetaljer = aldersjusteringsdetaljer,
                                    klageOmgjøringDetaljer =
                                        KlageOmgjøringDetaljer(
                                            resultatFraVedtak = resultatFraVedtak?.vedtaksid,
                                            resultatFraVedtakVedtakstidspunkt = resultatFraVedtak?.vedtakstidspunkt,
                                            manuellAldersjustering = true,
                                        ),
                                )
                            } else {
                                val avslagskode = if (p.resultat.beløp == null) resultat.avslagskode else null
                                grunnlagslisteRV
                                    .byggResultatBidragsberegning(
                                        p.periode,
                                        p.resultat.beløp,
                                        avslagskode,
                                        p.grunnlagsreferanseListe,
                                        resultat.ugyldigBeregning,
                                        grunnlagslisteRV.erResultatEndringUnderGrense(resultat.barn.referanse, p.grunnlagsreferanseListe),
                                        delvedtak?.vedtakstype ?: rv.vedtakstype,
                                        resultat.barn.ident!!,
                                        erEndeligVedtak = erEndeligVedtak,
                                    ).copy(
                                        erDirekteAvslag = avslagskode?.erDirekteAvslag() == true,
                                        resultatFraVedtak = resultatFraVedtak,
                                        klageOmgjøringDetaljer =
                                            KlageOmgjøringDetaljer(
                                                resultatFraVedtak = resultatFraVedtak?.vedtaksid,
                                                omgjøringsvedtak = erKlagevedtak,
                                                resultatFraVedtakVedtakstidspunkt = resultatFraVedtak?.vedtakstidspunkt,
                                                kanOpprette35c =
                                                    delvedtak?.let {
                                                        kanOpprette35C(
                                                            p.periode,
                                                            resultat.beregnTilDato!!,
                                                            resultat.opphørsdato,
                                                            it.vedtakstype,
                                                            erOpphør = p.resultat.beløp == null,
                                                            it.omgjøringsvedtak,
                                                        )
                                                    } ?: false,
                                                beregnTilDato = resultat.beregnTilDato,
                                                skalOpprette35c =
                                                    resultat.omgjøringsdetaljer
                                                        ?.paragraf35c
                                                        ?.any { it.vedtaksid == resultatFraVedtak?.vedtaksid && it.opprettParagraf35c } ==
                                                        true,
                                                manuellAldersjustering =
                                                    delvedtak?.vedtakstype == Vedtakstype.ALDERSJUSTERING &&
                                                        p.periode.fom.month.value == 7 &&
                                                        resultat.barn.grunnlagFraVedtak.any {
                                                            it.aldersjusteringForÅr == p.periode.fom.year &&
                                                                (it.vedtak != null || it.grunnlagFraOmgjøringsvedtak)
                                                        },
                                            ),
                                    )
                            }
                        }.toList()
                        .sortedBy { it.periode.fom }
                }

            val sistePeriode =
                rv.resultat.beregnetBarnebidragPeriodeListe
                    .maxByOrNull { it.periode.fom }
            val indeksår =
                rv.resultat.grunnlagListe.finnIndeksår(
                    resultat.barn.referanse,
                    sistePeriode?.periode ?: ÅrMånedsperiode(YearMonth.now(), null),
                    sistePeriode?.grunnlagsreferanseListe ?: emptyList(),
                )

            val perioder =
                (
                    opprettPerioder() +
                        opprettAldersjusteringPerioder(resultat)
                ).sortedBy { it.periode.fom }
            DelvedtakDto(
                type = rv.vedtakstype,
                delvedtak = rv.delvedtak,
                omgjøringsvedtak = resultatFraVedtak?.omgjøringsvedtak ?: rv.omgjøringsvedtak,
                beregnet =
                    resultatFraVedtak?.beregnet ?: rv.beregnet,
                vedtaksid = resultatFraVedtak?.vedtaksid,
                indeksår = indeksår,
                grunnlagFraVedtak = if (rv.delvedtak) resultat.barn.grunnlagFraVedtak else emptyList(),
                perioder = if (erEndeligVedtak) opprettIndeksreguleringsperioder(resultat, perioder) else perioder,
            )
        }?.toList() ?: emptyList()

fun List<GrunnlagDto>.finnDelberegningSjekkGrensePeriodeOgBarn(
    periode: ÅrMånedsperiode,
    søknadsbarnReferanse: String,
) = filtrerOgKonverterBasertPåFremmedReferanse<DelberegningEndringSjekkGrensePeriode>(
    Grunnlagstype.DELBEREGNING_ENDRING_SJEKK_GRENSE_PERIODE,
).find { it.innhold.periode == periode && it.gjelderBarnReferanse == søknadsbarnReferanse }

fun List<GrunnlagDto>.finnNesteIndeksårFraBeløpshistorikk(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Int? {
    val beløpshistorikk =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<BeløpshistorikkGrunnlag>(
            Grunnlagstype.BELØPSHISTORIKK_BIDRAG,
            grunnlagsreferanseListe,
        ).firstOrNull()
            ?: finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<BeløpshistorikkGrunnlag>(
                Grunnlagstype.BELØPSHISTORIKK_BIDRAG_18_ÅR,
                grunnlagsreferanseListe,
            ).firstOrNull()

    return beløpshistorikk?.innhold?.nesteIndeksreguleringsår
}

fun kanOpprette35C(
    periode: ÅrMånedsperiode,
    beregnTilDato: YearMonth,
    opphørsdato: YearMonth?,
    vedtakstype: Vedtakstype,
    erOpphør: Boolean,
    omgjøringsvedtak: Boolean,
) = !vedtakstype.erIndeksEllerAldersjustering && !omgjøringsvedtak &&
    periode.fom >= beregnTilDato && (opphørsdato == null || opphørsdato != periode.fom || (opphørsdato == periode.fom && !erOpphør))

fun List<GrunnlagDto>.finnNesteIndeksårFraPrivatAvtale(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Int? {
    val delberegningPrivatAvtaleLegacy =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningPrivatAvtale>(
            Grunnlagstype.DELBEREGNING_PRIVAT_AVTALE,
            grunnlagsreferanseListe,
        ).firstOrNull()
    if (delberegningPrivatAvtaleLegacy != null) {
        return delberegningPrivatAvtaleLegacy.innhold.nesteIndeksreguleringsår?.toInt()
    }
    val delberegningPrivatAvtale =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningIndeksreguleringPrivatAvtale>(
            Grunnlagstype.DELBEREGNING_INDEKSREGULERING_PRIVAT_AVTALE,
            grunnlagsreferanseListe,
        )

    return delberegningPrivatAvtale
        .map { it.innhold }
        .maxByOrNull { it.periode.fom }
        ?.nesteIndeksreguleringsår
        ?.toInt()
}

fun List<GrunnlagDto>.finnIndeksår(
    søknadsbarnReferanse: String,
    sistePeriode: ÅrMånedsperiode,
    periodereferanseListe: List<String>,
): Int {
    if (!erResultatEndringUnderGrense(søknadsbarnReferanse)) return Year.of(sistePeriode.fom.year).plusYears(1).value
    val nesteKalkulertIndeksår =
        if (YearMonth.now().isAfter(YearMonth.now().withMonth(7))) {
            Year.now().plusYears(1).value
        } else {
            Year.now().value
        }

    return if (!erResultatEndringUnderGrenseForPeriode(sistePeriode, søknadsbarnReferanse, periodereferanseListe)) {
        secureLogger.info {
            "Ingen resultat på finnDelberegningSjekkGrensePeriodeOgBarn for liste $this " +
                "og periode $sistePeriode og søknadsbarnReferanse $søknadsbarnReferanse"
        }
        nesteKalkulertIndeksår
    } else {
        finnDelberegningSjekkGrensePeriode(sistePeriode, søknadsbarnReferanse)?.let { endringUnderGrensePeriode ->
            val grunnlagsreferanseListe = endringUnderGrensePeriode.grunnlag.grunnlagsreferanseListe
            finnNesteIndeksårFraBeløpshistorikk(grunnlagsreferanseListe)
                ?: finnNesteIndeksårFraPrivatAvtale(grunnlagsreferanseListe)
        } ?: run {
            secureLogger.info {
                "Ingen resultat på finnDelberegningSjekkGrensePeriodeOgBarn for liste $this " +
                    "og periode $sistePeriode og søknadsbarnReferanse $søknadsbarnReferanse"
            }
            null
        } ?: nesteKalkulertIndeksår
    }
}

fun BeregnetSærbidragResultat.tilDto(behandling: Behandling) =
    let {
        val grunnlagsListe = grunnlagListe.toList()
        val periode = beregnetSærbidragPeriodeListe.first()
        grunnlagsListe.byggResultatSærbidragsberegning(
            periode.periode.fom.atDay(1),
            periode.resultat.beløp,
            periode.resultat.resultatkode,
            periode.grunnlagsreferanseListe,
            behandling.utgift?.tilBeregningDto() ?: UtgiftBeregningDto(),
            behandling.utgift
                ?.utgiftsposter
                ?.sorter()
                ?.map { it.tilDto() } ?: emptyList(),
            behandling.utgift?.maksGodkjentBeløpTaMed?.ifTrue { behandling.utgift?.maksGodkjentBeløp },
            beregnTilDato = behandling.finnBeregnTilDatoBehandling(),
        )
    }

fun List<GrunnlagDto>.byggResultatInntekter(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    bidragsmottakerReferanse: Grunnlagsreferanse? = null,
) = ResultatBeregningInntekterDto(
    inntektBarn = finnTotalInntektForRolle(grunnlagsreferanseListe, Rolletype.BARN),
    barnEndeligInntekt = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe)?.barnEndeligInntekt,
    inntektBP =
        finnTotalInntektForRolle(
            grunnlagsreferanseListe,
            Rolletype.BIDRAGSPLIKTIG,
        ),
    inntektBM =
        finnTotalInntektForRolle(
            grunnlagsreferanseListe,
            Rolletype.BIDRAGSMOTTAKER,
            bidragsmottakerReferanse,
        ),
)

fun List<GrunnlagDto>.byggResultatBidragsberegning(
    periode: ÅrMånedsperiode,
    resultat: BigDecimal?,
    resultatkode: Resultatkode?,
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    ugyldigBeregning: UgyldigBeregningDto?,
    erResultatEndringUnderGrense: Boolean,
    vedtakstype: Vedtakstype,
    barnIdent: Personident,
    erEndeligVedtak: Boolean = false,
): ResultatBarnebidragsberegningPeriodeDto {
    if (vedtakstype == Vedtakstype.ALDERSJUSTERING && !erAldersjusteringBisysVedtak()) {
        return byggPeriodeBeregningDtoForAldersjustering(
            grunnlagsreferanseListe,
            periode,
            vedtakstype,
            ugyldigBeregning,
            resultat,
            erEndeligVedtak,
        )
    } else if (vedtakstype == Vedtakstype.INDEKSREGULERING) {
        val sjablonIndeksfaktor =
            finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonSjablontallPeriode>(
                Grunnlagstype.SJABLON_SJABLONTALL,
                grunnlagsreferanseListe,
            ).find { it.innhold.sjablon == SjablonTallNavn.INDEKSREGULRERING_FAKTOR }
        return ResultatBarnebidragsberegningPeriodeDto(
            vedtakstype = vedtakstype,
            periode = periode,
            faktiskBidrag = resultat ?: BigDecimal.ZERO,
            resultatKode = Resultatkode.BEREGNET_BIDRAG,
            beregningsdetaljer =
                run {
                    BidragPeriodeBeregningsdetaljer(
                        indeksreguleringDetaljer =
                            IndeksreguleringDetaljer(
                                finnIndeksreguleringSluttberegning(grunnlagsreferanseListe),
                                sjablonIndeksfaktor?.innhold?.verdi ?: BigDecimal.ZERO,
                            ),
                    )
                },
        )
    } else {
        val erinnkrevingsgrunnlag = finnSøknadGrunnlag() != null && finnSøknadGrunnlag()!!.innkrevingsgrunnlag
        if (!erinnkrevingsgrunnlag) {
            finnResultatFraAnnenVedtak(grunnlagsreferanseListe)?.let {
                return byggPeriodeBeregningDtoForResultatFraAnnenVedtak(it, periode, barnIdent, vedtakstype)
            }
        }
        return byggPeriodeBeregningDto(
            grunnlagsreferanseListe,
            vedtakstype,
            resultatkode,
            barnIdent,
            periode,
            ugyldigBeregning,
            resultat,
            erResultatEndringUnderGrense,
        )
    }
}

private fun byggPeriodeBeregningDtoForResultatFraAnnenVedtak(
    grunnlag: ResultatFraVedtakGrunnlag,
    periode: ÅrMånedsperiode,
    barnIdent: Personident?,
    vedtakstype: Vedtakstype,
): ResultatBarnebidragsberegningPeriodeDto {
    var vedtakstype1 = vedtakstype
    if (grunnlag.vedtakstype == Vedtakstype.OPPHØR && grunnlag.vedtaksid == null) {
        return ResultatBarnebidragsberegningPeriodeDto(
            vedtakstype = Vedtakstype.OPPHØR,
            periode = periode,
            faktiskBidrag = BigDecimal.ZERO,
            erOpphør = true,
            resultatKode = Resultatkode.OPPHØR,
        )
    }
    val vedtak = hentVedtak(grunnlag.vedtaksid)
    val vedtakPeriode =
        vedtak!!
            .stønadsendringListe
            .find {
                it.kravhaver == barnIdent
            }!!
            .periodeListe
            .find { it.periode.inneholder(periode) }!!
    val barn = vedtak.grunnlagListe.hentPerson(barnIdent!!.verdi)
    val vedtakstype = if (vedtakstype1 == Vedtakstype.INNKREVING) vedtakstype1 else vedtak.type
    return vedtak.grunnlagListe
        .byggResultatBidragsberegning(
            periode,
            vedtakPeriode.beløp,
            Resultatkode.fraKode(vedtakPeriode.resultatkode),
            vedtakPeriode.grunnlagReferanseListe,
            null,
            barn?.let { vedtak.grunnlagListe.erResultatEndringUnderGrense(barn.referanse) } ?: false,
            vedtakstype,
            barnIdent,
        ).copy(
            klageOmgjøringDetaljer =
                KlageOmgjøringDetaljer(
                    omgjøringsvedtak = grunnlag.omgjøringsvedtak,
                ),
        )
}

private fun List<GrunnlagDto>.byggPeriodeBeregningDtoForAldersjustering(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    periode: ÅrMånedsperiode,
    vedtakstype: Vedtakstype,
    ugyldigBeregning: UgyldigBeregningDto?,
    resultat: BigDecimal?,
    erEndeligVedtak: Boolean,
): ResultatBarnebidragsberegningPeriodeDto {
    val bpsAndelKopi = finnKopiDelberegningBidragspliktigesAndel()!!
    val aldersjusteringDetaljer = finnAldersjusteringDetaljerGrunnlag()
    val delberegningUnderholdskostnad = finnDelberegningUnderholdskostnad(grunnlagsreferanseListe)
    val sluttberegningGrunnlag = finnSluttberegningIReferanser(grunnlagsreferanseListe)
    val sluttberegning =
        sluttberegningGrunnlag?.innholdTilObjekt<SluttberegningBarnebidragAldersjustering>()
    val bpsAndel =
        DelberegningBidragspliktigesAndel(
            periode = periode,
            andelBeløp = sluttberegning!!.bpAndelBeløp,
            endeligAndelFaktor =
                if (sluttberegning.deltBosted) {
                    sluttberegning.bpAndelFaktorVedDeltBosted!!
                } else {
                    bpsAndelKopi.endeligAndelFaktor
                },
            beregnetAndelFaktor = bpsAndelKopi.endeligAndelFaktor,
            barnetErSelvforsørget = false,
            barnEndeligInntekt = BigDecimal.ZERO,
        )
    return ResultatBarnebidragsberegningPeriodeDto(
        vedtakstype = vedtakstype,
        periode = periode,
        ugyldigBeregning = ugyldigBeregning?.resultatPeriode?.find { it.periode == periode },
        underholdskostnad = delberegningUnderholdskostnad?.underholdskostnad ?: BigDecimal.ZERO,
        faktiskBidrag = resultat ?: BigDecimal.ZERO,
        resultatKode = Resultatkode.BEREGNET_BIDRAG,
        beregnetBidrag = sluttberegning.beregnetBeløp,
        samværsfradrag = finnSamværsfradrag(grunnlagsreferanseListe),
        bpsAndelU = bpsAndel.endeligAndelFaktor,
        bpsAndelBeløp = sluttberegning.bpAndelBeløp,
        aldersjusteringDetaljer = aldersjusteringDetaljer,
        endeligVedtak = erEndeligVedtak,
        erOpphør = resultat == null,
        beregningsdetaljer =
            run {
                BidragPeriodeBeregningsdetaljer(
                    samværsfradrag =
                        finnAldersjusteringDelberegningSamværsfradrag(
                            grunnlagsreferanseListe,
                        ),
                    delberegningUnderholdskostnad = delberegningUnderholdskostnad,
                    bpsAndel = bpsAndel,
                    sluttberegningAldersjustering = sluttberegning,
                    bpHarEvne = false,
                    forskuddssats = BigDecimal.ZERO,
                )
            },
    )
}

private fun List<GrunnlagDto>.byggPeriodeBeregningDto(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    vedtakstype: Vedtakstype,
    resultatkode: Resultatkode?,
    barnIdent: Personident,
    periode: ÅrMånedsperiode,
    ugyldigBeregning: UgyldigBeregningDto?,
    resultat: BigDecimal?,
    erResultatEndringUnderGrense: Boolean,
): ResultatBarnebidragsberegningPeriodeDto {
    val bpsAndel = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe)
    val delberegningUnderholdskostnad = finnDelberegningUnderholdskostnad(grunnlagsreferanseListe)
    val sluttberegningGrunnlag =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)?.takeIf {
            it.type ==
                Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG
        }
    val sluttberegning = byggSluttberegningV2(grunnlagsreferanseListe)
    val personobjekt = hentPerson(barnIdent.verdi)

    val delberegningGrensePeriode =
        if (vedtakstype == Vedtakstype.OPPHØR || resultatkode?.erDirekteAvslag() == true) {
            null
        } else {
            hentPersonNyesteIdent(barnIdent.verdi)?.let { barn ->
                finnDelberegningSjekkGrensePeriode(periode, barn.referanse)
                    ?: finnDelberegningSjekkGrensePeriode(ÅrMånedsperiode(periode.fom, null), barn.referanse)
            }
        }
    return ResultatBarnebidragsberegningPeriodeDto(
        vedtakstype = vedtakstype,
        periode = periode,
        periodeHarSlåttUtTilFF = periodeHarSlåttUtTilFF(periode),
        ugyldigBeregning = ugyldigBeregning?.resultatPeriode?.find { it.periode == periode },
        underholdskostnad = delberegningUnderholdskostnad?.underholdskostnad ?: BigDecimal.ZERO,
        faktiskBidrag = resultat ?: BigDecimal.ZERO,
        erOpphør = resultat == null,
        resultatKode =
            if (erResultatEndringUnderGrense) {
                Resultatkode.INGEN_ENDRING_UNDER_GRENSE
            } else {
                resultatkode
            },
        beregnetBidrag = sluttberegning?.beregnetBeløp ?: BigDecimal.ZERO,
        samværsfradrag = finnSamværsfradrag(grunnlagsreferanseListe),
        bpsAndelU =
            if (sluttberegning?.bidragJustertForDeltBosted == true) {
                sluttberegning.bpAndelAvUVedDeltBostedFaktor
            } else {
                bpsAndel?.endeligAndelFaktor ?: BigDecimal.ZERO
            },
        bpsAndelBeløp = bpsAndel?.andelBeløp ?: BigDecimal.ZERO,
        erDirekteAvslag = resultatkode?.erDirekteAvslag() ?: false,
        erEndringUnderGrense = erResultatEndringUnderGrense,
        erBeregnetAvslag =
            sluttberegning != null &&
                (sluttberegning.barnetErSelvforsørget || sluttberegning.ikkeOmsorgForBarnet),
        beregningsdetaljer =
            if (vedtakstype == Vedtakstype.INNKREVING) {
                null
            } else if (resultatkode?.erAvslag() == true) {
                BidragPeriodeBeregningsdetaljer(
                    forskuddssats = finnForskuddssats(grunnlagsreferanseListe),
                    barnetilleggBM = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSMOTTAKER),
                    barnetilleggBP = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSPLIKTIG),
                    sluttberegning = sluttberegning,
                    bpHarEvne = false,
                )
            } else {
                val delberegningBPsEvne = finnDelberegningBidragsevne(grunnlagsreferanseListe)
                val forholdsmessigFordeling = byggGrunnlagForholdsmessigFordeling(grunnlagsreferanseListe)
                BidragPeriodeBeregningsdetaljer(
                    delberegningBidragsevne = delberegningBPsEvne,
                    forholdsmessigFordeling = forholdsmessigFordeling,
                    sluttberegning1 =
                        sluttberegningGrunnlag
                            ?.takeIf { it.erSluttberegningGammelStruktur() }
                            ?.innholdTilObjekt<SluttberegningBarnebidrag>(),
                    endringUnderGrense = delberegningGrensePeriode?.innhold,
                    barnetilleggBM = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSMOTTAKER),
                    barnetilleggBP = finnBarnetillegg(grunnlagsreferanseListe, Grunnlagstype.PERSON_BIDRAGSPLIKTIG),
                    samværsfradrag =
                        finnDelberegningSamværsfradrag(
                            grunnlagsreferanseListe,
                        ),
                    delberegningUnderholdskostnad = delberegningUnderholdskostnad,
                    forskuddssats = finnForskuddssats(grunnlagsreferanseListe),
                    delberegningBidragspliktigesBeregnedeTotalBidrag =
                        finnDelberegningBPsBeregnedeTotalbidrag(
                            grunnlagsreferanseListe,
                        ),
                    bpsAndel = bpsAndel,
                    sluttberegning = sluttberegning,
                    antallBarnIHusstanden = finnAntallBarnIHusstanden(grunnlagsreferanseListe),
                    inntekter = byggResultatInntekter(grunnlagsreferanseListe, personobjekt?.bidragsmottakerReferanse),
                    voksenIHusstanden = finnBorMedAndreVoksne(grunnlagsreferanseListe),
                    enesteVoksenIHusstandenErEgetBarn =
                        finnEnesteVoksenIHusstandenErEgetBarn(
                            grunnlagsreferanseListe,
                        ),
                    bpHarEvne = delberegningBPsEvne?.let { it.bidragsevne > BigDecimal.ZERO } ?: false,
                )
            },
    )
}

fun List<GrunnlagDto>.byggResultatSærbidragsberegning(
    virkningstidspunkt: LocalDate,
    resultat: BigDecimal?,
    resultatkode: Resultatkode,
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    beregning: UtgiftBeregningDto,
    utgiftsposter: List<UtgiftspostDto>,
    maksGodkjentBeløp: BigDecimal?,
    beregnTilDato: LocalDate,
) = ResultatSærbidragsberegningDto(
    periode =
        ÅrMånedsperiode(
            virkningstidspunkt,
            beregnTilDato,
        ),
    resultat = resultat ?: BigDecimal.ZERO,
    resultatKode = resultatkode,
    beregning = beregning,
    forskuddssats = finnForskuddssats(grunnlagsreferanseListe),
    bpsAndel = finnDelberegningBidragspliktigesAndel(grunnlagsreferanseListe),
    antallBarnIHusstanden = finnAntallBarnIHusstanden(grunnlagsreferanseListe),
    inntekter = byggResultatInntekter(grunnlagsreferanseListe),
    utgiftsposter = utgiftsposter,
    maksGodkjentBeløp = maksGodkjentBeløp,
    delberegningUtgift = finnDelberegningUtgift(grunnlagsreferanseListe),
    delberegningBidragsevne = finnDelberegningBidragsevne(grunnlagsreferanseListe),
    delberegningBidragspliktigesBeregnedeTotalBidrag = finnDelberegningBPsBeregnedeTotalbidrag(grunnlagsreferanseListe),
    voksenIHusstanden = finnBorMedAndreVoksne(grunnlagsreferanseListe),
    enesteVoksenIHusstandenErEgetBarn = finnEnesteVoksenIHusstandenErEgetBarn(grunnlagsreferanseListe),
    erDirekteAvslag = resultatkode.erDirekteAvslag(),
)

fun List<ResultatForskuddsberegningBarn>.tilDto(vedtakstype: Vedtakstype? = null) =
    map { resultat ->
        val grunnlagsListe = resultat.resultat.grunnlagListe.toList()
        ResultatBeregningBarnDto(
            barn = resultat.barn,
            perioder =
                resultat.resultat.beregnetForskuddPeriodeListe.map { periode ->
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = periode.periode,
                        beløp = periode.resultat.belop,
                        vedtakstype = vedtakstype,
                        resultatKode = periode.resultat.kode,
                        regel = periode.resultat.regel,
                        sivilstand = grunnlagsListe.finnSivilstandForPeriode(periode.grunnlagsreferanseListe),
                        inntekt =
                            grunnlagsListe.finnTotalInntektForRolle(periode.grunnlagsreferanseListe),
                        antallBarnIHusstanden =
                            grunnlagsListe
                                .finnAntallBarnIHusstanden(periode.grunnlagsreferanseListe)
                                .toInt(),
                    )
                },
        )
    }

fun List<GrunnlagDto>.finnSivilstandForPeriode(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Sivilstandskode? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val sivilstandPeriode =
        find {
            it.type == Grunnlagstype.SIVILSTAND_PERIODE &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        }
    return sivilstandPeriode?.innholdTilObjekt<SivilstandPeriode>()?.sivilstand
}

fun List<GrunnlagDto>.finnSamværsfradrag(grunnlagsreferanseListe: List<Grunnlagsreferanse>): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val samværsfradrag =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG, sluttberegning).firstOrNull()
    return samværsfradrag?.innholdTilObjekt<DelberegningSamværsfradrag>()?.beløp
        ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnAntallBarnIHusstanden(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Double {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return 0.0
    val delberegningBarnIHusstanden =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND, sluttberegning).firstOrNull()
    return delberegningBarnIHusstanden?.innholdTilObjekt<DelberegningBarnIHusstand>()?.antallBarn
        ?: 0.0
}

fun List<GrunnlagDto>.finnDelberegningBidragspliktigesAndel(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): DelberegningBidragspliktigesAndel? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragspliktigesAndel>()
}

fun List<GrunnlagDto>.erAldersjusteringBisysVedtak(): Boolean = finnAldersjusteringDetaljerGrunnlag() == null

fun List<GrunnlagDto>.erAldersjusteringNyLøsning(): Boolean = finnAldersjusteringDetaljerGrunnlag() != null

fun List<GrunnlagDto>.finnAldersjusteringDetaljerReferanse(): String? {
    val grunnlag =
        find {
            it.type == Grunnlagstype.ALDERSJUSTERING_DETALJER
        } ?: return null
    return grunnlag.referanse
}

fun List<GrunnlagDto>.finnIndeksreguleringSluttberegning(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): SluttberegningIndeksregulering? {
    val grunnlag =
        filtrerBasertPåEgenReferanser(Grunnlagstype.SLUTTBEREGNING_INDEKSREGULERING, grunnlagsreferanseListe).firstOrNull() ?: return null
    return grunnlag.innholdTilObjekt<SluttberegningIndeksregulering>()
}

fun List<GrunnlagDto>.finnAldersjusteringDetaljerGrunnlag(søknadsbarnReferanse: String? = null): AldersjusteringDetaljerGrunnlag? {
    val grunnlag =
        find {
            it.type == Grunnlagstype.ALDERSJUSTERING_DETALJER &&
                (søknadsbarnReferanse == null || it.gjelderBarnReferanse == søknadsbarnReferanse)
        } ?: return null
    return grunnlag.innholdTilObjekt<AldersjusteringDetaljerGrunnlag>()
}

fun List<GrunnlagDto>.finnKopiDelberegningBidragspliktigesAndel(): KopiDelberegningBidragspliktigesAndel? {
    val delberegningBidragspliktigesAndel =
        find {
            it.type == Grunnlagstype.KOPI_DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL
        } ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<KopiDelberegningBidragspliktigesAndel>()
}

fun List<GrunnlagDto>.finnDelberegningBPsBeregnedeTotalbidrag(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): DelberegningBidragspliktigesBeregnedeTotalbidragDto? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegning =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_BEREGNEDE_TOTALBIDRAG, sluttberegning)
            .firstOrNull() ?: return null
    val delberegningObjekt = delberegning.innholdTilObjekt<DelberegningBidragspliktigesBeregnedeTotalbidrag>()

    return DelberegningBidragspliktigesBeregnedeTotalbidragDto(
        bidragspliktigesBeregnedeTotalbidrag = delberegningObjekt.bidragspliktigesBeregnedeTotalbidrag,
        periode = delberegningObjekt.periode,
        beregnetBidragPerBarnListe =
            delberegningObjekt.beregnetBidragPerBarnListe.map {
                DelberegningBidragspliktigesBeregnedeTotalbidragDto.BeregnetBidragPerBarnDto(
                    beregnetBidragPerBarn = it,
                    personidentBarn = hentPersonMedReferanse(it.gjelderBarn)?.personIdent!!,
                )
            },
    )
}

fun List<GrunnlagDto>.finnEnesteVoksenIHusstandenErEgetBarn(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Boolean? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull()
            ?: return null
    val bosstatuser = finnGrunnlagSomErReferertAv(Grunnlagstype.BOSTATUS_PERIODE, delberegningBidragspliktigesAndel)
    val borMedVoksenBarn =
        bosstatuser
            .find { it.gjelderReferanse != bidragspliktig?.referanse }
            ?.innholdTilObjekt<BostatusPeriode>() != null
    val borIkkeMedAndreVoksne =
        bosstatuser
            .find { it.gjelderReferanse == bidragspliktig?.referanse }
            ?.innholdTilObjekt<BostatusPeriode>()
            ?.bostatus == Bostatuskode.BOR_IKKE_MED_ANDRE_VOKSNE
    return borMedVoksenBarn && borIkkeMedAndreVoksne
}

fun List<GrunnlagDto>.finnBorMedAndreVoksne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): Boolean? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, sluttberegning).firstOrNull()
            ?: return null
    return delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningVoksneIHusstand>().borMedAndreVoksne
}

fun List<GrunnlagDto>.finnDelberegningUtgift(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningUtgift? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningBidragspliktigesAndelReferanser =
        find {
            it.type == Grunnlagstype.DELBEREGNING_BIDRAGSPLIKTIGES_ANDEL &&
                sluttberegning.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    val delberegningUtgift =
        find {
            it.type == Grunnlagstype.DELBEREGNING_UTGIFT &&
                delberegningBidragspliktigesAndelReferanser.grunnlagsreferanseListe.contains(
                    it.referanse,
                )
        } ?: return null
    return delberegningUtgift.innholdTilObjekt<DelberegningUtgift>()
}

fun List<GrunnlagDto>.finnAldersjusteringDelberegningSamværsfradrag(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningSamværsfradragGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val samværsperiodeGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.KOPI_SAMVÆRSPERIODE,
            delberegningSamværsfradragGrunnlag.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val delberegningSamværsfradrag = delberegningSamværsfradragGrunnlag.innholdTilObjekt<DelberegningSamværsfradrag>()
    return BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag(
        samværsfradrag = delberegningSamværsfradrag.beløp,
        samværsklasse = samværsperiodeGrunnlag.innholdTilObjekt<KopiSamværsperiodeGrunnlag>().samværsklasse,
        gjennomsnittligSamværPerMåned = BigDecimal.ZERO,
    )
}

fun List<GrunnlagDto>.finnDelberegningSamværsfradrag(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningSamværsfradragGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val samværsperiodeGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.SAMVÆRSPERIODE,
            delberegningSamværsfradragGrunnlag.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val delberegningSamværsklasseGrunnlag =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE,
            samværsperiodeGrunnlag.grunnlagsreferanseListe,
        ).firstOrNull()

    val delberegningSamværsfradrag = delberegningSamværsfradragGrunnlag.innholdTilObjekt<DelberegningSamværsfradrag>()
    return BidragPeriodeBeregningsdetaljer.BeregningsdetaljerSamværsfradrag(
        samværsfradrag = delberegningSamværsfradrag.beløp,
        samværsklasse = samværsperiodeGrunnlag.innholdTilObjekt<SamværsklassePeriode>().samværsklasse,
        gjennomsnittligSamværPerMåned =
            delberegningSamværsklasseGrunnlag
                ?.innholdTilObjekt<DelberegningSamværsklasse>()
                ?.gjennomsnittligSamværPerMåned ?: BigDecimal.ZERO,
    )
}

fun List<InnholdMedReferanse<DelberegningUnderholdskostnad>>.tilUnderholdskostnadDto(
    underholdBeregning: List<GrunnlagDto>,
    erBisysVedtak: Boolean,
) = this
    .map { delberegning ->
        val it = delberegning.innhold
        UnderholdskostnadDto(
            stønadTilBarnetilsyn = it.barnetilsynMedStønad ?: BigDecimal.ZERO,
            tilsynsutgifter = it.nettoTilsynsutgift ?: BigDecimal.ZERO,
            periode =
                DatoperiodeDto(
                    it.periode.fom.atDay(1),
                    it.periode.til
                        ?.atDay(1)
                        ?.minusDays(1),
                ),
            forbruk = it.forbruksutgift,
            barnetrygd = it.barnetrygd,
            boutgifter = it.boutgift,
            forpleining = it.forpleining,
            total = it.underholdskostnad,
            beregningsdetaljer =
                if (underholdBeregning.erAldersjusteringNyLøsning()) {
                    val vedtaksid = underholdBeregning.finnAldersjusteringDetaljerGrunnlag()?.grunnlagFraVedtak
                    if (vedtaksid != null) {
                        val opprinneligVedtak = hentVedtak(vedtaksid)!!
                        underholdBeregning.hentPersonMedReferanse(delberegning.gjelderBarnReferanse)?.let { person ->
                            val stønadsendring =
                                opprinneligVedtak.stønadsendringListe
                                    .find {
                                        it.kravhaver.verdi == person.personIdent
                                    } ?: opprinneligVedtak.stønadsendringListe.first()
                            val sistePeriode =
                                stønadsendring.periodeListe
                                    .maxBy { it.periode.fom }
                            opprinneligVedtak.grunnlagListe.tilUnderholdskostnadDetaljer(
                                sistePeriode.grunnlagReferanseListe,
                            )
                        }
                    } else {
                        null
                    }
                } else if (erBisysVedtak) {
                    underholdBeregning.tilUnderholdskostnadDetaljer(
                        delberegning.grunnlag.grunnlagsreferanseListe,
                    )
                } else {
                    underholdBeregning.tilUnderholdskostnadDetaljer(
                        delberegning.grunnlag.grunnlagsreferanseListe,
                    )
                },
        )
    }.toSet()

fun List<GrunnlagDto>.tilUnderholdskostnadDetaljer(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): UnderholdskostnadPeriodeBeregningsdetaljer? {
    val nettoTilsyn =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningNettoTilsynsutgift>(
            Grunnlagstype.DELBEREGNING_NETTO_TILSYNSUTGIFT,
            grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    val sjablonMaksTilsyn =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonMaksTilsynPeriode>(
            Grunnlagstype.SJABLON_MAKS_TILSYN,
            grunnlagsreferanseListe,
        )
    val sjablonMaksfradrag =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonMaksFradragPeriode>(
            Grunnlagstype.SJABLON_MAKS_FRADRAG,
            grunnlagsreferanseListe,
        )
    val sjablonSkattesats =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<SjablonSjablontallPeriode>(
            Grunnlagstype.SJABLON_SJABLONTALL,
            nettoTilsyn.grunnlag.grunnlagsreferanseListe,
        ).find { it.innhold.sjablon == SjablonTallNavn.SKATT_ALMINNELIG_INNTEKT_PROSENT }
    val maksTilsynBeløp = sjablonMaksTilsyn.firstOrNull()?.innhold?.maksBeløpTilsyn ?: BigDecimal.ZERO
    val sumTilsynsutgifter = nettoTilsyn.innhold.tilsynsutgiftBarnListe.sumOf { it.sumTilsynsutgifter }
    val erBegrensetAvMaksTilsyn =
        nettoTilsyn.innhold.totalTilsynsutgift.setScale(0, RoundingMode.HALF_UP) != sumTilsynsutgifter.setScale(0, RoundingMode.HALF_UP)
    val skattesatsFaktor = sjablonSkattesats?.innhold?.verdi?.divide(BigDecimal(100), 10, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
    return UnderholdskostnadPeriodeBeregningsdetaljer(
        tilsynsutgifterBarn =
            nettoTilsyn.innhold.tilsynsutgiftBarnListe
                .mapNotNull { fu ->
                    tilsynsutgifterBarn(grunnlagsreferanseListe, fu)
                }.sortedWith(
                    compareByDescending<UnderholdskostnadDto.TilsynsutgiftBarn> { it.gjelderBarn.medIBehandlingen }
                        .thenByDescending { it.gjelderBarn.fødselsdato },
                ),
        erBegrensetAvMaksTilsyn = erBegrensetAvMaksTilsyn,
        sjablonMaksTilsynsutgift = maksTilsynBeløp,
        totalTilsynsutgift = nettoTilsyn.innhold.totalTilsynsutgift,
        sumTilsynsutgifter = sumTilsynsutgifter,
        fordelingFaktor = nettoTilsyn.innhold.andelTilsynsutgiftFaktor,
        skattefradragPerBarn = nettoTilsyn.innhold.skattefradragPerBarn,
        sjablonMaksFradrag = sjablonMaksfradrag.firstOrNull()?.innhold?.maksBeløpFradrag ?: BigDecimal.ZERO,
        skattefradragTotalTilsynsutgift = nettoTilsyn.innhold.skattefradragTotalTilsynsutgift,
        skattefradragMaksFradrag = nettoTilsyn.innhold.skattefradragMaksfradrag,
        skattefradrag = nettoTilsyn.innhold.skattefradrag,
        skattesatsFaktor = skattesatsFaktor,
        antallBarnBMUnderTolvÅr = nettoTilsyn.innhold.antallBarnBMUnderTolvÅr,
        antallBarnBMBeregnet = nettoTilsyn.innhold.antallBarnBMBeregnet,
        antallBarnMedTilsynsutgifter = nettoTilsyn.innhold.antallBarnMedTilsynsutgifter,
        bruttoTilsynsutgift = nettoTilsyn.innhold.bruttoTilsynsutgift,
        justertBruttoTilsynsutgift = nettoTilsyn.innhold.justertBruttoTilsynsutgift,
        nettoTilsynsutgift = nettoTilsyn.innhold.nettoTilsynsutgift,
    )
}

fun List<GrunnlagDto>.tilsynsutgifterBarn(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    tilsynsutgiftBarn: TilsynsutgiftBarn,
): UnderholdskostnadDto.TilsynsutgiftBarn? {
    val faktiskUtgiftPeriode =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<FaktiskUtgiftPeriode>(
            Grunnlagstype.FAKTISK_UTGIFT_PERIODE,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn } ?: return null

    val delberegningFaktiskUtgiftPeriode =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningFaktiskTilsynsutgift>(
            Grunnlagstype.DELBEREGNING_FAKTISK_UTGIFT,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn } ?: return null

    val delberegningTilleggsstønad =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningTilleggsstønad>(
            Grunnlagstype.DELBEREGNING_TILLEGGSSTØNAD,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn }

    val tilleggsstønadPeriode =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<TilleggsstønadPeriode>(
            Grunnlagstype.TILLEGGSSTØNAD_PERIODE,
            grunnlagsreferanseListe,
        ).find { it.gjelderBarnReferanse == tilsynsutgiftBarn.gjelderBarn }
    val personGrunnlag = hentPersonMedReferanse(tilsynsutgiftBarn.gjelderBarn)
    val person = personGrunnlag?.innholdTilObjekt<Person>()
    val navn = if (person?.navn.isNullOrEmpty()) hentPersonVisningsnavn(person?.ident?.verdi) else person?.navn
    return UnderholdskostnadDto.TilsynsutgiftBarn(
        gjelderBarn =
            PersoninfoDto(
                1,
                person?.ident,
                navn,
                person?.fødselsdato,
                medIBehandlingen =
                    personGrunnlag?.type == Grunnlagstype.PERSON_SØKNADSBARN,
            ),
        totalTilsynsutgift = faktiskUtgiftPeriode.innhold.faktiskUtgiftBeløp,
        faktiskUtgiftBeregnet = delberegningFaktiskUtgiftPeriode.innhold.beregnetBeløp,
        beløp = tilsynsutgiftBarn.sumTilsynsutgifter,
        kostpenger = faktiskUtgiftPeriode.innhold.kostpengerBeløp,
        tilleggsstønadDagsats = tilleggsstønadPeriode?.innhold?.beløpDagsats,
        tilleggsstønadBeløp = tilleggsstønadPeriode?.innhold?.beløp,
        beløpstype = tilleggsstønadPeriode?.innhold?.beløpstype ?: InntektBeløpstype.DAGSATS,
        tilleggsstønad = delberegningTilleggsstønad?.innhold?.beregnetBeløp,
    )
}

fun List<GrunnlagDto>.finnDelberegningerPrivatAvtalePerioder(gjelderBarnReferanse: String): List<DelberegningIndeksreguleringPrivatAvtale> {
    val delberegningLegacy =
        this
            .filtrerOgKonverterBasertPåEgenReferanse<DelberegningPrivatAvtale>(
                Grunnlagstype.DELBEREGNING_PRIVAT_AVTALE,
            ).firstOrNull { it.gjelderBarnReferanse == gjelderBarnReferanse }
    if (delberegningLegacy != null) {
        return delberegningLegacy.innhold.perioder.map {
            DelberegningIndeksreguleringPrivatAvtale(
                periode = it.periode,
                nesteIndeksreguleringsår = delberegningLegacy.innhold.nesteIndeksreguleringsår,
                indeksreguleringFaktor = it.indeksreguleringFaktor,
                indeksregulertBeløp = it.beløp,
            )
        }
    }
    val perioder =
        this
            .filtrerOgKonverterBasertPåEgenReferanse<DelberegningIndeksreguleringPrivatAvtale>(
                Grunnlagstype.DELBEREGNING_INDEKSREGULERING_PRIVAT_AVTALE,
            ).filter { it.gjelderBarnReferanse == gjelderBarnReferanse }
            .map { it.innhold }
    return perioder
}

fun List<GrunnlagDto>.finnDelberegningerPrivatAvtaleReferanser(gjelderBarnReferanse: String): List<String> =
    this
        .filtrerOgKonverterBasertPåEgenReferanse<DelberegningPrivatAvtale>(
            Grunnlagstype.DELBEREGNING_PRIVAT_AVTALE,
        ).firstOrNull { it.gjelderBarnReferanse == gjelderBarnReferanse }
        ?.let { listOf(it.referanse) }
        ?: this
            .filtrerOgKonverterBasertPåEgenReferanse<DelberegningIndeksreguleringPrivatAvtale>(
                Grunnlagstype.DELBEREGNING_INDEKSREGULERING_PRIVAT_AVTALE,
            ).filter { it.gjelderBarnReferanse == gjelderBarnReferanse }
            .map { it.referanse }

fun List<GrunnlagDto>.finnAlleDelberegningerPrivatAvtalePeriode(
    gjelderBarnReferanse: String,
): List<DelberegningIndeksreguleringPrivatAvtale> =
    this
        .finnDelberegningerPrivatAvtalePerioder(gjelderBarnReferanse)
        .sortedBy {
            it.periode.fom
        }

fun List<GrunnlagDto>.finnAlleDelberegningUnderholdskostnad(rolle: Rolle): List<InnholdMedReferanse<DelberegningUnderholdskostnad>> =
    this
        .filtrerOgKonverterBasertPåEgenReferanse<DelberegningUnderholdskostnad>(
            Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD,
        ).filter { hentPersonMedReferanse(it.gjelderBarnReferanse)?.personIdent == rolle.personident?.verdi }
        .sortedBy {
            it.innhold.periode.fom
        }

fun List<GrunnlagDto>.finnBarnetillegg(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    personGrunnlagstype: Grunnlagstype,
): DelberegningBarnetilleggDto {
    val personGrunnlag = find { it.type == personGrunnlagstype } ?: return DelberegningBarnetilleggDto()
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return DelberegningBarnetilleggDto()
    val nettoBarnetillegg =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningNettoBarnetillegg>(
            Grunnlagstype.DELBEREGNING_NETTO_BARNETILLEGG,
            sluttberegning,
        ).find { it.gjelderReferanse == personGrunnlag.referanse }

    val delberegningBarnetilleggSkattesats =
        nettoBarnetillegg?.let {
            finnOgKonverterGrunnlagSomErReferertAv<DelberegningBarnetilleggSkattesats>(
                Grunnlagstype.DELBEREGNING_BARNETILLEGG_SKATTESATS,
                nettoBarnetillegg.grunnlag,
            ).firstOrNull()
        }
    val delberegningSumInntekt =
        delberegningBarnetilleggSkattesats?.let {
            finnOgKonverterGrunnlagSomErReferertAv<DelberegningSumInntekt>(
                Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                nettoBarnetillegg.grunnlag,
            ).firstOrNull()
        }
    return DelberegningBarnetilleggDto(
        sumInntekt = delberegningSumInntekt?.innhold?.totalinntekt ?: BigDecimal.ZERO,
        skattFaktor = delberegningBarnetilleggSkattesats?.innhold?.skattFaktor ?: BigDecimal.ZERO,
        delberegningSkattesats = delberegningBarnetilleggSkattesats?.innhold,
        sumBruttoBeløp = nettoBarnetillegg?.innhold?.summertBruttoBarnetillegg ?: BigDecimal.ZERO,
        sumNettoBeløp = nettoBarnetillegg?.innhold?.summertNettoBarnetillegg ?: BigDecimal.ZERO,
        barnetillegg =
            nettoBarnetillegg
                ?.innhold
                ?.barnetilleggTypeListe
                ?.map {
                    DelberegningBarnetilleggDto.BarnetilleggDetaljerDto(
                        bruttoBeløp = it.bruttoBarnetillegg,
                        nettoBeløp = it.nettoBarnetillegg,
                        visningsnavn = it.barnetilleggType.visningsnavn.intern,
                    )
                } ?: emptyList(),
    )
}

fun List<GrunnlagDto>.periodeHarSlåttUtTilFF(periode: ÅrMånedsperiode) = perioderSlåttUtTilFF().map { it.periode }.contains(periode)

fun List<GrunnlagDto>.perioderSlåttUtTilFF(): List<PeriodeSlåttUtTilFF> {
    val andelBidragsevne =
        filtrerOgKonverterBasertPåFremmedReferanse<DelberegningAndelAvBidragsevne>(
            Grunnlagstype.DELBEREGNING_ANDEL_AV_BIDRAGSEVNE,
        )

    return andelBidragsevne
        .filter {
            it.innhold.andelAvSumBidragTilFordelingFaktor < BigDecimal.ONE && !it.innhold.harBPFullEvne
        }.map {
            val grunnlag25ProsentAvInntekt = finnGrunnlag25ProsentAvInntekt(it.grunnlag.grunnlagsreferanseListe)
            PeriodeSlåttUtTilFF(
                it.innhold.periode,
                grunnlag25ProsentAvInntekt?.innhold?.erEvneJustertNedTil25ProsentAvInntekt == true,
            )
        }
}

internal fun List<GrunnlagDto>.hentSamvær(gjelderReferanse: String): List<SamværsperiodeGrunnlag> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSPERIODE)
        .filter {
            it.gjelderBarnReferanse == gjelderReferanse || it.gjelderReferanse == gjelderReferanse
        }.map { it.innholdTilObjekt<SamværsperiodeGrunnlag>() }

fun List<GrunnlagDto>.harOpprettetForholdsmessigFordeling(): Boolean =
    hentBehandlingDetaljer()?.opprettetForholdsmessigFordeling == true ||
        // Opprettet FF
        hentSøknader().any {
            it.behandlingstype.erForholdsmessigFordeling
        } ||
        // Opprett FF når alle barna er i samme søknad. Tilfelle hvor det er valgt ulik virkningstidspunkt for barna
        filtrerOgKonverterBasertPåEgenReferanse<VirkningstidspunktGrunnlag>(Grunnlagstype.VIRKNINGSTIDSPUNKT).any {
            if (it.gjelderBarnReferanse == null) return@any false
            val minsteSamværsperiode = hentSamvær(it.gjelderBarnReferanse!!).minOfOrNull { it.periode.fom } ?: return@any false
            it.innhold.virkningstidspunkt < minsteSamværsperiode.toLocalDate()
        } ||
        // Flere søknader = Opprettet FF vanligvis
        hentSøknader().size > 1

fun List<GrunnlagDto>.harSlåttUtTilForholdsmessigFordeling(): Boolean = perioderSlåttUtTilFF().isNotEmpty()

fun List<GrunnlagDto>.byggGrunnlagForholdsmessigFordeling(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
): ForholdsmessigFordelingBeregningsdetaljer? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val sumBidragTilBeregning =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningSumBidragTilFordeling>(
            Grunnlagstype.DELBEREGNING_SUM_BIDRAG_TIL_FORDELING,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull()
            ?: return null
    val bidragTilFordeling =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningBidragTilFordeling>(
            Grunnlagstype.DELBEREGNING_BIDRAG_TIL_FORDELING,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null
    val andelAvBidragsevne =
        finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningAndelAvBidragsevne>(
            Grunnlagstype.DELBEREGNING_ANDEL_AV_BIDRAGSEVNE,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null

    // TODO: Legg til også privat avtale og utlandskbidrag
    val bidragTilFordelingSøknadsbarnGrunnlag =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningBidragTilFordeling>(
            Grunnlagstype.DELBEREGNING_BIDRAG_TIL_FORDELING,
            sumBidragTilBeregning.grunnlag,
        ).sortedBy { it.gjelderBarnReferanse == bidragTilFordeling.gjelderBarnReferanse }

    val bidragTilFordelingSøknadsbarn =
        bidragTilFordelingSøknadsbarnGrunnlag.map {
            val barn = hentPersonMedReferanse(it.gjelderBarnReferanse!!)!!.personObjekt
            ForholdsmessigFordelingBidragTilFordelingBarn(
                utenlandskbidrag = false,
                privatAvtale = false,
                erSøknadsbarn = true,
                beregnetBidrag =
                    ForholdsmessigFordelingBidragTilFordelingBarn.BeregnetBidragBarnDto(
                        // Verdiene under er ikke interessant å vise for barn som er i søknaden
                        saksnummer = Saksnummer(""),
                        samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                        løpendeBeløp = BigDecimal.ZERO,
                        faktiskBeløp = BigDecimal.ZERO,
                        beregnetBidrag = it.innhold.bidragTilFordeling,
                        beregnetBeløp = BigDecimal.ZERO,
                        reduksjonUnderholdskostnad = BigDecimal.ZERO,
                        samværsfradrag = BigDecimal.ZERO,
                    ),
                barn =
                    PersoninfoDto(ident = barn.ident, fødselsdato = barn.fødselsdato, navn = barn.navn),
                bidragTilFordeling = it.innhold.bidragTilFordeling,
            )
        }
    val bidragTilFordelingAndreBarn =
        finnBidragTilFordelingLøpendeBidrag(sumBidragTilBeregning.grunnlag, sumBidragTilBeregning.innhold.periode)
    val bidragTilFordelingAlle = bidragTilFordelingSøknadsbarn + bidragTilFordelingAndreBarn
    return ForholdsmessigFordelingBeregningsdetaljer(
        sumBidragTilFordeling = sumBidragTilBeregning.innhold.sumBidragTilFordeling,
        sumPrioriterteBidragTilFordeling = sumBidragTilBeregning.innhold.sumPrioriterteBidragTilFordeling,
        erKompletteGrunnlagForAlleLøpendeBidrag = sumBidragTilBeregning.innhold.erKompletteGrunnlagForAlleLøpendeBidrag,
        bidragTilFordelingForBarnet = bidragTilFordeling.innhold.bidragTilFordeling,
        andelAvEvneBeløp = andelAvBidragsevne.innhold.andelAvEvneBeløp,
        andelAvSumBidragTilFordelingFaktor = andelAvBidragsevne.innhold.andelAvSumBidragTilFordelingFaktor,
        sumBidragTilFordelingJustertForPrioriterteBidrag = andelAvBidragsevne.innhold.sumBidragTilFordelingJustertForPrioriterteBidrag,
        bidragEtterFordeling = andelAvBidragsevne.innhold.bidragEtterFordeling,
        evneJustertForPrioriterteBidrag = andelAvBidragsevne.innhold.evneJustertForPrioriterteBidrag,
        harBPFullEvne = andelAvBidragsevne.innhold.harBPFullEvne,
        erForholdsmessigFordelt = periodeHarSlåttUtTilFF(sluttberegning.sluttberegningPeriode()),
        bidragTilFordelingAlle = bidragTilFordelingAlle,
        finnesBarnMedLøpendeBidragSomIkkeErSøknadsbarn = bidragTilFordelingAlle.any { !it.erSøknadsbarn && !it.erBidragSomIkkeKanFordeles },
        sumBidragTilFordelingSøknadsbarn =
            bidragTilFordelingAlle
                .filter {
                    it.erSøknadsbarn && it.beregnetBidrag != null
                }.sumOf { it.beregnetBidrag!!.beregnetBidrag },
        sumBidragTilFordelingIkkeSøknadsbarn =
            bidragTilFordelingAlle
                .filter {
                    !it.erSøknadsbarn && it.beregnetBidrag != null && !it.privatAvtale
                }.sumOf { it.beregnetBidrag!!.beregnetBidrag },
        sumBidragTilFordelingPrivatAvtale =
            bidragTilFordelingAlle
                .filter {
                    !it.erSøknadsbarn && it.beregnetBidrag != null && it.privatAvtale && !it.erBidragSomIkkeKanFordeles
                }.sumOf { it.beregnetBidrag!!.beregnetBidrag },
        sumBidragSomIkkeKanFordeles =
            bidragTilFordelingAlle
                .filter {
                    !it.erSøknadsbarn && it.beregnetBidrag != null && it.erBidragSomIkkeKanFordeles
                }.sumOf { it.beregnetBidrag!!.beregnetBidrag },
    )
}

private fun List<GrunnlagDto>.finnBidragTilFordelingLøpendeBidrag(
    fraGrunnlag: BaseGrunnlag,
    periode: ÅrMånedsperiode,
): List<ForholdsmessigFordelingBidragTilFordelingBarn> {
    val bidragTilFordelingLøpendeBidrag =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningBidragTilFordelingLøpendeBidrag>(
            Grunnlagstype.DELBEREGNING_BIDRAG_TIL_FORDELING_LØPENDE_BIDRAG,
            fraGrunnlag,
        )

    val løpendeBidrag =
        bidragTilFordelingLøpendeBidrag.mapNotNull {
            val barn = hentPersonMedReferanse(it.gjelderBarnReferanse!!)!!.personObjekt
            val grunnlagSamværsfradrag =
                finnOgKonverterGrunnlagSomErReferertAv<DelberegningSamværsfradrag>(
                    Grunnlagstype.DELBEREGNING_SAMVÆRSFRADRAG,
                    it.grunnlag,
                ).firstOrNull()
            val løpendeBidrag =
                finnOgKonverterGrunnlagSomErReferertAv<LøpendeBidragPeriode>(
                    Grunnlagstype.LØPENDE_BIDRAG_PERIODE,
                    it.grunnlag,
                ).firstOrNull()?.innhold ?: return@mapNotNull null
            val valutakurser =
                finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ValutakursGrunnlag>(
                    Grunnlagstype.VALUTAKURS_GRUNNLAG,
                    it.grunnlag.grunnlagsreferanseListe,
                ).firstOrNull()
                    ?.innhold
            val valutakursNOKTilValuta =
                valutakurser
                    ?.valutakursListe
                    ?.find { vl -> vl.valutakode1 == it.innhold.valutakode && vl.valutakode2 == Valutakode.NOK }
                    ?.valutakurs ?: BigDecimal.ONE
            val valutakursValutaTilNOK =
                valutakurser
                    ?.valutakursListe
                    ?.find { vl -> vl.valutakode1 == it.innhold.valutakode && vl.valutakode2 == Valutakode.NOK }
                    ?.valutakurs ?: BigDecimal.ONE
            ForholdsmessigFordelingBidragTilFordelingBarn(
                utenlandskbidrag = !it.innhold.erNorskBidrag,
                oppfostringsbidrag = it.innhold.erOppfostringsbidrag,
                privatAvtale = false,
                erSøknadsbarn = false,
                bidragTilFordeling = it.innhold.bidragTilFordelingNOK,
                barn =
                    PersoninfoDto(ident = barn.ident, fødselsdato = barn.fødselsdato, navn = barn.navn),
                beregnetBidrag =
                    ForholdsmessigFordelingBidragTilFordelingBarn.BeregnetBidragBarnDto(
                        saksnummer = løpendeBidrag.saksnummer,
                        samværsklasse = løpendeBidrag.samværsklasse ?: Samværsklasse.SAMVÆRSKLASSE_0,
                        løpendeBeløp = løpendeBidrag.løpendeBeløp,
                        faktiskBeløp = løpendeBidrag.faktiskBeløp,
                        beregnetBidrag = it.innhold.bidragTilFordelingNOK,
                        beregnetBeløp = løpendeBidrag.beregnetBeløp,
                        valutakode = løpendeBidrag.valutakode,
                        valutakurs = valutakursNOKTilValuta ?: BigDecimal.ONE,
                        reduksjonUnderholdskostnad = it.innhold.reduksjonUnderholdskostnad,
                        samværsfradrag = grunnlagSamværsfradrag?.innhold?.beløp?.multiply(valutakursValutaTilNOK) ?: BigDecimal.ZERO,
                    ),
            )
        }

    val privatAvtaleBeregnetPerioder =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningBidragTilFordelingPrivatAvtale>(
            Grunnlagstype.DELBEREGNING_BIDRAG_TIL_FORDELING_PRIVAT_AVTALE,
            fraGrunnlag,
        )

    val privatAvtaler =
        privatAvtaleBeregnetPerioder.map {
            val barn = hentPersonMedReferanse(it.gjelderBarnReferanse!!)!!.personObjekt
            val privatAvtalePerioder =
                finnOgKonverterGrunnlagSomErReferertAv<PrivatAvtalePeriodeGrunnlag>(
                    Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG,
                    it.grunnlag,
                )

            val periodeSomOverlapper = privatAvtalePerioder.find { it.innhold.periode.inneholder(periode) }!!.innhold

            val indeksregulertGrunnalg =
                finnOgKonverterGrunnlagSomErReferertAv<DelberegningIndeksreguleringPrivatAvtale>(
                    Grunnlagstype.DELBEREGNING_INDEKSREGULERING_PRIVAT_AVTALE,
                    it.grunnlag,
                ).firstOrNull()

            val valutakurser =
                finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ValutakursGrunnlag>(
                    Grunnlagstype.VALUTAKURS_GRUNNLAG,
                    it.grunnlag.grunnlagsreferanseListe,
                ).firstOrNull()
                    ?.innhold
            val valutakursNOKTilValuta =
                valutakurser
                    ?.valutakursListe
                    ?.find { vl -> vl.valutakode1 == it.innhold.valutakode && vl.valutakode2 == Valutakode.NOK }
                    ?.valutakurs ?: BigDecimal.ONE
            val valutakursValutaTilNOK =
                valutakurser
                    ?.valutakursListe
                    ?.find { vl -> vl.valutakode1 == it.innhold.valutakode && vl.valutakode2 == Valutakode.NOK }
                    ?.valutakurs ?: BigDecimal.ONE
            ForholdsmessigFordelingBidragTilFordelingBarn(
                utenlandskbidrag = !it.innhold.erNorskBidrag,
                privatAvtale = true,
                erSøknadsbarn = false,
                bidragTilFordeling = it.innhold.bidragTilFordelingNOK,
                barn =
                    PersoninfoDto(ident = barn.ident, fødselsdato = barn.fødselsdato, navn = barn.navn),
                beregnetBidrag =
                    ForholdsmessigFordelingBidragTilFordelingBarn.BeregnetBidragBarnDto(
                        saksnummer = Saksnummer(""), // TODO,
                        samværsklasse = periodeSomOverlapper.samværsklasse ?: Samværsklasse.SAMVÆRSKLASSE_0,
                        løpendeBeløp = periodeSomOverlapper.beløp,
                        indeksreguleringFaktor = indeksregulertGrunnalg?.innhold?.indeksreguleringFaktor,
                        faktiskBeløp = periodeSomOverlapper.beløp,
                        beregnetBidrag = it.innhold.bidragTilFordelingNOK,
                        beregnetBeløp = it.innhold.indeksregulertBeløp.multiply(valutakursValutaTilNOK),
                        valutakode = it.innhold.valutakode,
                        valutakurs = valutakursNOKTilValuta ?: BigDecimal.ONE,
                        reduksjonUnderholdskostnad = BigDecimal.ZERO,
                        samværsfradrag = it.innhold.samværsfradrag?.multiply(valutakursValutaTilNOK) ?: BigDecimal.ZERO,
                    ),
            )
        }

    val privatAvtalePeriodeLegacy =
        finnOgKonverterGrunnlagSomErReferertAv<DelberegningPrivatAvtale>(
            Grunnlagstype.DELBEREGNING_PRIVAT_AVTALE,
            fraGrunnlag,
        )

    val privatAvtalerLegacy =
        privatAvtalePeriodeLegacy.map {
            val barn = hentPersonMedReferanse(it.gjelderBarnReferanse!!)!!.personObjekt
            val privatAvtalePerioder =
                finnOgKonverterGrunnlagSomErReferertAv<PrivatAvtalePeriodeGrunnlag>(
                    Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG,
                    fraGrunnlag,
                )

            val periodeSomOverlapper = privatAvtalePerioder.find { it.innhold.periode.overlapper(periode) }!!.innhold
            val periodeBeregnet =
                it.innhold.perioder
                    .filter { it.periode.inneholder(periode) }
                    .maxBy { it.periode.fom }

            ForholdsmessigFordelingBidragTilFordelingBarn(
                utenlandskbidrag = false,
                privatAvtale = true,
                erSøknadsbarn = false,
                bidragTilFordeling = periodeBeregnet.beløp,
                barn =
                    PersoninfoDto(ident = barn.ident, fødselsdato = barn.fødselsdato, navn = barn.navn),
                beregnetBidrag =
                    ForholdsmessigFordelingBidragTilFordelingBarn.BeregnetBidragBarnDto(
                        saksnummer = Saksnummer(""), // TODO,
                        samværsklasse = periodeSomOverlapper.samværsklasse ?: Samværsklasse.SAMVÆRSKLASSE_0,
                        løpendeBeløp = periodeSomOverlapper.beløp,
                        indeksreguleringFaktor = periodeBeregnet.indeksreguleringFaktor,
                        faktiskBeløp = periodeSomOverlapper.beløp,
                        beregnetBidrag = periodeBeregnet.beløp,
                        beregnetBeløp = periodeBeregnet.beløp,
                        valutakode = periodeSomOverlapper.valutakode ?: Valutakode.NOK,
                        reduksjonUnderholdskostnad = BigDecimal.ZERO,
                        samværsfradrag = BigDecimal.ZERO,
                    ),
            )
        }
    return privatAvtaler + løpendeBidrag + privatAvtalerLegacy
}

fun List<GrunnlagDto>.finnGrunnlag25ProsentAvInntekt(grunnlagsreferanseListe: List<Grunnlagsreferanse>) =
    finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<DelberegningEvne25ProsentAvInntekt>(
        Grunnlagstype.DELBEREGNING_EVNE_25PROSENTAVINNTEKT,
        grunnlagsreferanseListe,
    ).firstOrNull()

fun List<GrunnlagDto>.byggSluttberegningV2(grunnlagsreferanseListe: List<Grunnlagsreferanse>): SluttberegningBarnebidrag2? {
    val sluttberegning = byggSluttberegningBarnebidragDetaljer(grunnlagsreferanseListe) ?: return null
    return SluttberegningBarnebidrag2(
        bidragJustertNedTilEvne = sluttberegning.bidragJustertNedTilEvne,
        nettoBidragEtterSamværsfradrag = sluttberegning.nettoBidragEtterSamværsfradrag,
        uMinusNettoBarnetilleggBM = sluttberegning.uMinusNettoBarnetilleggBM,
        bpAndelAvUVedDeltBostedBeløp = sluttberegning.bpAndelAvUVedDeltBostedBeløp,
        bpAndelAvUVedDeltBostedFaktor = sluttberegning.bpAndelAvUVedDeltBostedFaktor,
        bidragJustertForDeltBosted = sluttberegning.bidragJustertForDeltBosted,
        bidragJustertForNettoBarnetilleggBP = sluttberegning.bidragJustertForNettoBarnetilleggBP,
        bruttoBidragEtterBarnetilleggBP = sluttberegning.bruttoBidragEtterBarnetilleggBP,
        bidragJustertNedTil25ProsentAvInntekt = sluttberegning.bidragJustertNedTil25ProsentAvInntekt,
        bruttoBidragJustertForEvneOg25Prosent = sluttberegning.bruttoBidragJustertForEvneOg25Prosent,
        barnetErSelvforsørget = sluttberegning.barnetErSelvforsørget,
        ikkeOmsorgForBarnet = sluttberegning.ikkeOmsorgForBarnet,
        beregnetBeløp = sluttberegning.beregnetBeløp,
        resultatBeløp = sluttberegning.resultatBeløp,
        løpendeBidrag = sluttberegning.løpendeBidrag,
        løpendeForskudd = sluttberegning.løpendeForskudd,
        begrensetRevurderingUtført = sluttberegning.begrensetRevurderingUtført,
        nettoBidragEtterBarnetilleggBM = sluttberegning.nettoBidragEtterBarnetilleggBM,
        bruttoBidragEtterBarnetilleggBM = sluttberegning.bruttoBidragEtterBarnetilleggBM,
        bidragJustertForNettoBarnetilleggBM = sluttberegning.bidragJustertForNettoBarnetilleggBM,
        resultat = sluttberegning.resultat,
        resultatVisningsnavn = sluttberegning.resultatVisningsnavn,
    )
}

fun List<GrunnlagDto>.finnDelberegningUnderholdskostnad(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningUnderholdskostnad? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null
    val delberegningUnderholdskostnad =
        finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
            Grunnlagstype.DELBEREGNING_UNDERHOLDSKOSTNAD,
            sluttberegning.grunnlagsreferanseListe,
        ).firstOrNull() ?: return null
    return delberegningUnderholdskostnad.innholdTilObjekt<DelberegningUnderholdskostnad>()
}

fun List<GrunnlagDto>.finnDelberegningBidragsevne(grunnlagsreferanseListe: List<Grunnlagsreferanse>): DelberegningBidragsevneDto? {
    val sluttberegning = finnSluttberegningIReferanser(grunnlagsreferanseListe) ?: return null

    val delberegningBidragspliktigesAndel =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BIDRAGSEVNE, sluttberegning)
            .firstOrNull() ?: return null
    val delberegningBoforhold =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BOFORHOLD, delberegningBidragspliktigesAndel)
            .firstOrNull() ?: return null
    val delberegningVoksneIHusstand =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_VOKSNE_I_HUSSTAND, delberegningBoforhold)
            .firstOrNull()
            ?.innholdTilObjekt<DelberegningVoksneIHusstand>() ?: return null
    val delberegningBarnIHusstanden =
        finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND, delberegningBoforhold)
            .firstOrNull()
            ?.innholdTilObjekt<DelberegningBarnIHusstand>() ?: return null
    val sjablonBidragsevne =
        finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_BIDRAGSEVNE, delberegningBidragspliktigesAndel)
            .firstOrNull()
            ?.innholdTilObjekt<SjablonBidragsevnePeriode>()
    val sjablonUnderholdEgnebarnIHusstand =
        find {
            it.type == Grunnlagstype.SJABLON_SJABLONTALL &&
                delberegningBidragspliktigesAndel.grunnlagsreferanseListe.contains(
                    it.referanse,
                ) &&
                it.innholdTilObjekt<SjablonSjablontallPeriode>().sjablon == SjablonTallNavn.UNDERHOLD_EGNE_BARN_I_HUSSTAND_BELØP
        }?.innholdTilObjekt<SjablonSjablontallPeriode>()
    val delberegningBidragsevne = delberegningBidragspliktigesAndel.innholdTilObjekt<DelberegningBidragsevne>()
    return DelberegningBidragsevneDto(
        bidragsevne = delberegningBidragsevne.beløp,
        sumInntekt25Prosent = delberegningBidragsevne.sumInntekt25Prosent,
        skatt =
            DelberegningBidragsevneDto.Skatt(
                sumSkattFaktor = delberegningBidragsevne.skatt.sumSkattFaktor,
                sumSkatt = delberegningBidragsevne.skatt.sumSkatt,
                skattAlminneligInntekt = delberegningBidragsevne.skatt.skattAlminneligInntekt,
                trinnskatt = delberegningBidragsevne.skatt.trinnskatt,
                trygdeavgift = delberegningBidragsevne.skatt.trygdeavgift,
            ),
        underholdEgneBarnIHusstand =
            DelberegningBidragsevneDto.UnderholdEgneBarnIHusstand(
                årsbeløp = delberegningBidragsevne.underholdBarnEgenHusstand,
                sjablon = sjablonUnderholdEgnebarnIHusstand?.verdi ?: BigDecimal.ZERO,
                antallBarnIHusstanden = delberegningBarnIHusstanden.antallBarn,
            ),
        utgifter =
            DelberegningBidragsevneDto.BidragsevneUtgifterBolig(
                underholdBeløp = sjablonBidragsevne?.underholdBeløp ?: BigDecimal.ZERO,
                boutgiftBeløp = sjablonBidragsevne?.boutgiftBeløp ?: BigDecimal.ZERO,
                borMedAndreVoksne = delberegningVoksneIHusstand.borMedAndreVoksne,
            ),
    )
}

fun List<GrunnlagDto>.finnForskuddssats(grunnlagsreferanseListe: List<Grunnlagsreferanse>): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    return finnGrunnlagSomErReferertAv(Grunnlagstype.SJABLON_SJABLONTALL, sluttberegning)
        .find { it.innholdTilObjekt<SjablonSjablontallPeriode>().sjablon == SjablonTallNavn.FORSKUDDSSATS_BELØP }
        ?.innholdTilObjekt<SjablonSjablontallPeriode>()
        ?.verdi ?: BigDecimal.ZERO
}

fun List<GrunnlagDto>.finnTotalInntektForRolle(
    grunnlagsreferanseListe: List<Grunnlagsreferanse>,
    rolletype: Rolletype? = null,
    gjelderReferanse: String? = null,
): BigDecimal {
    val sluttberegning =
        finnSluttberegningIReferanser(grunnlagsreferanseListe)
            ?: return BigDecimal.ZERO
    val gjelderReferanse = gjelderReferanse ?: hentAllePersoner().find { it.type == rolletype?.tilGrunnlagstype() }?.referanse
    val delberegningSumInntekter = finnGrunnlagSomErReferertAv(Grunnlagstype.DELBEREGNING_SUM_INNTEKT, sluttberegning)
    val delberegningSumInntektForRolle =
        if (gjelderReferanse.isNullOrEmpty()) {
            delberegningSumInntekter.firstOrNull()
        } else {
            delberegningSumInntekter.find {
                it.gjelderReferanse ==
                    gjelderReferanse
            }
        }
    return delberegningSumInntektForRolle?.innholdTilObjekt<DelberegningSumInntekt>()?.totalinntekt
        ?: BigDecimal.ZERO
}
