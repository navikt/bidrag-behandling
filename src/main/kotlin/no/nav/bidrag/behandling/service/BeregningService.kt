package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBidragsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v1.beregning.UgyldigBeregningDto
import no.nav.bidrag.behandling.dto.v1.beregning.opprettBegrunnelse
import no.nav.bidrag.behandling.dto.v1.beregning.tilBeregningFeilmelding
import no.nav.bidrag.behandling.transformers.beregning.validerForSærbidrag
import no.nav.bidrag.behandling.transformers.finnDelberegningBPsBeregnedeTotalbidrag
import no.nav.bidrag.behandling.transformers.grunnlag.opprettAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnInnkrevesFraDato
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.fjernMidlertidligPersonobjekterBMsbarn
import no.nav.bidrag.beregn.barnebidrag.service.AldersjusteresManueltException
import no.nav.bidrag.beregn.barnebidrag.service.AldersjusteringOrchestrator
import no.nav.bidrag.beregn.barnebidrag.service.BeregnBasertPåVedtak
import no.nav.bidrag.beregn.barnebidrag.service.BidragsberegningOrkestrator
import no.nav.bidrag.beregn.barnebidrag.service.FinnesEtterfølgendeVedtakMedVirkningstidspunktFørPåklagetVedtak
import no.nav.bidrag.beregn.barnebidrag.service.SkalIkkeAldersjusteresException
import no.nav.bidrag.beregn.barnebidrag.utils.toYearMonth
import no.nav.bidrag.beregn.core.bo.Periode
import no.nav.bidrag.beregn.core.bo.Sjablon
import no.nav.bidrag.beregn.core.bo.SjablonInnhold
import no.nav.bidrag.beregn.core.bo.SjablonNøkkel
import no.nav.bidrag.beregn.core.bo.SjablonPeriode
import no.nav.bidrag.beregn.core.dto.SjablonPeriodeCore
import no.nav.bidrag.beregn.core.exception.BegrensetRevurderingLikEllerLavereEnnLøpendeBidragException
import no.nav.bidrag.beregn.core.exception.BegrensetRevurderingLøpendeForskuddManglerException
import no.nav.bidrag.beregn.core.service.mapper.CoreMapper
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.beregn.særbidrag.BeregnSærbidragApi
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.beregning.BidragsevneBeregning
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.AntallBarnIHusstand
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.BostatusVoksneIHusstand
import no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo.GrunnlagBeregning
import no.nav.bidrag.beregn.særbidrag.core.felles.bo.SjablonListe
import no.nav.bidrag.commons.service.sjablon.SjablonProvider
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.sjablon.SjablonTallNavn
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.sak.Stønadsid
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import no.nav.bidrag.transport.felles.tilVisningsnavn
import org.checkerframework.checker.units.qual.s
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatBeregning as ResultatBeregningBidrag
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode as ResultatPeriodeBidrag
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatBeregning as ResultatBeregningSærbidrag
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatPeriode as ResultatPeriodeSærbidrag

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() =
    ResultatRolle(tilPersonident(), hentNavn(), fødselsdato, innbetaltBeløp, tilGrunnlagsreferanse(), grunnlagFraVedtakListe)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
    private val mapper: VedtakGrunnlagMapper,
    private val aldersjusteringOrchestrator: AldersjusteringOrchestrator,
    private val beregnBarnebidrag: BidragsberegningOrkestrator,
) {
    private val beregnApi = BeregnForskuddApi()
    private val beregnSærbidragApi = BeregnSærbidragApi()

    fun beregneForskudd(behandling: Behandling): List<ResultatForskuddsberegningBarn> {
        behandling.run {
            mapper.run {
                validering.run { validerForBeregningForskudd() }
                return if (avslag != null) {
                    søknadsbarn.map {
                        tilResultatAvslag(it)
                    }
                } else {
                    søknadsbarn.map { rolle ->
                        val beregningRequest = byggGrunnlagForBeregning(behandling, rolle)

                        try {
                            ResultatForskuddsberegningBarn(
                                rolle.mapTilResultatBarn(),
                                beregnApi.beregn(beregningRequest.beregnGrunnlag!!),
                            )
                        } catch (e: Exception) {
                            LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                        }
                    }
                }
            }
        }
    }

    fun beregneSærbidrag(behandling: Behandling): BeregnetSærbidragResultat {
        mapper.validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val søknasdbarn = behandling.søknadsbarn.first()
        return if (mapper.validering.run { behandling.erDirekteAvslagUtenBeregning() }) {
            behandling.tilResultatAvslagSærbidrag()
        } else {
            try {
                val beregningRequest =
                    mapper.byggGrunnlagForBeregning(behandling, søknasdbarn)
                beregnSærbidragApi
                    .beregn(
                        beregningRequest.beregnGrunnlag!!,
                        behandling.klagedetaljer?.opprinneligVedtakstype ?: behandling.vedtakstype,
                    ).let { resultat ->
                        resultat.validerForSærbidrag()
                        resultat
                    }
            } catch (e: Exception) {
                LOGGER.warn(e) { "Det skjedde en feil ved beregning av særbidrag: ${e.message}" }
                throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
            }
        }
    }

    fun beregneBidrag(
        behandling: Behandling,
        endeligBeregning: Boolean = true,
    ): List<ResultatBidragsberegningBarn> {
        mapper.validering.run {
            behandling.validerForBeregningBidrag()
        }

        return if (behandling.vedtakstype == Vedtakstype.ALDERSJUSTERING) {
            beregnBidragAldersjustering(behandling)
        } else if (mapper.validering.run { behandling.erDirekteAvslagUtenBeregning() } && !endeligBeregning) {
            behandling.søknadsbarn.map { behandling.tilResultatAvslagBidrag(it) }
        } else {
            behandling.søknadsbarn.map { søknasdbarn ->
                val grunnlagBeregning =
                    mapper.byggGrunnlagForBeregning(behandling, søknasdbarn, endeligBeregning)
                try {
                    val resultat =
                        beregnBarnebidrag
                            .utførBidragsberegning(grunnlagBeregning)
                    ResultatBidragsberegningBarn(
                        ugyldigBeregning = behandling.tilBeregningFeilmelding(),
                        barn = søknasdbarn.mapTilResultatBarn(),
                        vedtakstype = behandling.vedtakstype,
                        resultatVedtak = resultat,
                        avslaskode = søknasdbarn.avslag,
                        klagedetaljer = behandling.klagedetaljer,
                        innkrevesFraDato = behandling.finnInnkrevesFraDato(søknasdbarn),
                        resultat =
                            resultat.resultatVedtakListe
                                .find {
                                    behandling.erKlageEllerOmgjøring && it.klagevedtak || !behandling.erKlageEllerOmgjøring
                                }?.resultat
                                ?.let {
                                    it.copy(
                                        grunnlagListe =
                                            (it.grunnlagListe + grunnlagBeregning.beregnGrunnlag!!.grunnlagListe)
                                                .toSet()
                                                .toList()
                                                .fjernMidlertidligPersonobjekterBMsbarn(),
                                    )
                                } ?: BeregnetBarnebidragResultat(),
                    )
                } catch (e: FinnesEtterfølgendeVedtakMedVirkningstidspunktFørPåklagetVedtak) {
                    val beregnTilDato =
                        søknasdbarn
                            .behandling
                            .finnBeregnTilDatoBehandling(søknasdbarn.opphørsdato?.toYearMonth(), søknasdbarn)
                    ResultatBidragsberegningBarn(
                        ugyldigBeregning =
                            UgyldigBeregningDto(
                                tittel = "Ugyldig perioder",
                                vedtaksliste = e.vedtak,
                                begrunnelse =
                                    "En eller flere etterfølgende vedtak har virkningstidpunkt " +
                                        "som starter før beregningsperioden ${søknasdbarn.virkningstidspunkt.tilVisningsnavn()} - ${beregnTilDato.tilVisningsnavn()}",
                            ),
                        barn = søknasdbarn.mapTilResultatBarn(),
                        vedtakstype = behandling.vedtakstype,
                        klagedetaljer = behandling.klagedetaljer,
                        innkrevesFraDato = behandling.finnInnkrevesFraDato(søknasdbarn),
                        resultat = BeregnetBarnebidragResultat(),
                    )
                } catch (e: BegrensetRevurderingLikEllerLavereEnnLøpendeBidragException) {
                    ResultatBidragsberegningBarn(
                        ugyldigBeregning = e.opprettBegrunnelse(),
                        barn = søknasdbarn.mapTilResultatBarn(),
                        vedtakstype = behandling.vedtakstype,
                        klagedetaljer = behandling.klagedetaljer,
                        innkrevesFraDato = behandling.finnInnkrevesFraDato(søknasdbarn),
                        resultat =
                            e.data.copy(
                                grunnlagListe =
                                    (e.data.grunnlagListe + grunnlagBeregning.beregnGrunnlag!!.grunnlagListe)
                                        .toSet()
                                        .toList()
                                        .fjernMidlertidligPersonobjekterBMsbarn(),
                            ),
                    )
                } catch (e: BegrensetRevurderingLøpendeForskuddManglerException) {
                    ResultatBidragsberegningBarn(
                        ugyldigBeregning = e.opprettBegrunnelse(),
                        barn = søknasdbarn.mapTilResultatBarn(),
                        vedtakstype = behandling.vedtakstype,
                        klagedetaljer = behandling.klagedetaljer,
                        innkrevesFraDato = behandling.finnInnkrevesFraDato(søknasdbarn),
                        resultat =
                            e.data.copy(
                                grunnlagListe =
                                    (e.data.grunnlagListe + grunnlagBeregning.beregnGrunnlag!!.grunnlagListe)
                                        .toSet()
                                        .toList()
                                        .fjernMidlertidligPersonobjekterBMsbarn(),
                            ),
                    )
                } catch (e: Exception) {
                    LOGGER.warn(e) { "Det skjedde en feil ved beregning av barnebidrag: ${e.message}" }
                    throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                }
            }
        }
    }

    private fun beregnBidragAldersjustering(behandling: Behandling): List<ResultatBidragsberegningBarn> {
        val søknadsbarn = behandling.søknadsbarn.first()
        val stønadsid =
            Stønadsid(
                behandling.stonadstype!!,
                Personident(søknadsbarn.ident!!),
                Personident(behandling.bidragspliktig!!.ident!!),
                Saksnummer(behandling.saksnummer),
            )
        try {
            if (søknadsbarn.grunnlagFraVedtak == null) return emptyList()
            val beregning =
                aldersjusteringOrchestrator.utførAldersjustering(
                    stønadsid,
                    behandling.virkningstidspunkt!!.year,
                    BeregnBasertPåVedtak(søknadsbarn.grunnlagFraVedtak),
                    søknadsbarn.opphørsdato?.let { YearMonth.from(it) },
                )

            val søknadsbarnGrunnlag = beregning.beregning.grunnlagListe.hentPersonNyesteIdent(søknadsbarn.ident)!!
            return listOf(
                ResultatBidragsberegningBarn(
                    barn = søknadsbarn.mapTilResultatBarn(),
                    vedtakstype = behandling.vedtakstype,
                    klagedetaljer = behandling.klagedetaljer,
                    innkrevesFraDato = behandling.finnInnkrevesFraDato(søknadsbarn),
                    resultat =
                        beregning.beregning.copy(
                            grunnlagListe =
                                beregning.beregning.grunnlagListe +
                                    listOf(
                                        behandling.opprettAldersjusteringDetaljerGrunnlag(
                                            søknadsbarnGrunnlag.referanse,
                                            søknadsbarn = søknadsbarn,
                                            vedtaksidBeregning = søknadsbarn.grunnlagFraVedtak,
                                        ),
                                    ),
                        ),
                ),
            )
        } catch (e: SkalIkkeAldersjusteresException) {
            val søknadsbarnGrunnlag = søknadsbarn.tilGrunnlagPerson()
            val aldersjusteringGrunnlag =
                behandling.opprettAldersjusteringDetaljerGrunnlag(
                    søknadsbarnGrunnlag.referanse,
                    søknadsbarn = søknadsbarn,
                    aldersjustert = false,
                    begrunnelser = e.begrunnelser.map { it.name },
                    vedtaksidBeregning = søknadsbarn.grunnlagFraVedtak,
                )
            return listOf(
                ResultatBidragsberegningBarn(
                    barn = søknadsbarn.mapTilResultatBarn(),
                    vedtakstype = behandling.vedtakstype,
                    klagedetaljer = behandling.klagedetaljer,
                    innkrevesFraDato = behandling.finnInnkrevesFraDato(søknadsbarn),
                    resultat =
                        BeregnetBarnebidragResultat(
                            grunnlagListe = listOf(søknadsbarnGrunnlag, aldersjusteringGrunnlag),
                            beregnetBarnebidragPeriodeListe = emptyList(),
                        ),
                ),
            )
        } catch (e: AldersjusteresManueltException) {
            val søknadsbarnGrunnlag = søknadsbarn.tilGrunnlagPerson()
            val aldersjusteringGrunnlag =
                behandling.opprettAldersjusteringDetaljerGrunnlag(
                    søknadsbarnGrunnlag.referanse,
                    søknadsbarn = søknadsbarn,
                    aldersjustert = false,
                    aldersjusteresManuelt = true,
                    vedtaksidBeregning = søknadsbarn.grunnlagFraVedtak,
                    begrunnelser = listOf(e.begrunnelse.name),
                )
            return listOf(
                ResultatBidragsberegningBarn(
                    barn = søknadsbarn.mapTilResultatBarn(),
                    vedtakstype = behandling.vedtakstype,
                    klagedetaljer = behandling.klagedetaljer,
                    innkrevesFraDato = behandling.finnInnkrevesFraDato(søknadsbarn),
                    resultat =
                        BeregnetBarnebidragResultat(
                            grunnlagListe = listOf(søknadsbarnGrunnlag, aldersjusteringGrunnlag),
                            beregnetBarnebidragPeriodeListe = emptyList(),
                        ),
                ),
            )
        } catch (e: Exception) {
            LOGGER.warn(e) { "Det skjedde en feil ved beregning av barnebidrag: ${e.message}" }
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
        }
    }

    fun beregneForskudd(behandlingsid: Long): List<ResultatForskuddsberegningBarn> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneForskudd(behandling)
    }

    fun beregneSærbidrag(behandlingsid: Long): BeregnetSærbidragResultat {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneSærbidrag(behandling)
    }

    fun beregneBidrag(
        behandlingsid: Long,
        endeligBeregning: Boolean = true,
    ): List<ResultatBidragsberegningBarn> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneBidrag(behandling, endeligBeregning)
    }

    private fun Behandling.tilResultatAvslagBidrag(barn: Rolle) =
        ResultatBidragsberegningBarn(
            barn = barn.mapTilResultatBarn(),
            avslaskode = avslag,
            vedtakstype = vedtakstype,
            klagedetaljer = klagedetaljer,
            resultat =
                BeregnetBarnebidragResultat(
                    beregnetBarnebidragPeriodeListe =
                        listOf(
                            ResultatPeriodeBidrag(
                                grunnlagsreferanseListe = emptyList(),
                                periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                                resultat =
                                    ResultatBeregningBidrag(
                                        beløp = BigDecimal.ZERO,
                                    ),
                            ),
                        ),
                    grunnlagListe = emptyList(),
                ),
        )

    private fun Behandling.tilResultatAvslagSærbidrag() =
        BeregnetSærbidragResultat(
            beregnetSærbidragPeriodeListe =
                listOf(
                    ResultatPeriodeSærbidrag(
                        grunnlagsreferanseListe = emptyList(),
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, virkningstidspunkt!!.plusMonths(1)),
                        resultat =
                            ResultatBeregningSærbidrag(
                                beløp = BigDecimal.ZERO,
                                resultatkode = mapper.validering.run { tilSærbidragAvslagskode()!! },
                            ),
                    ),
                ),
            grunnlagListe = emptyList(),
        )

    private fun Behandling.tilResultatAvslag(barn: Rolle) =
        ResultatForskuddsberegningBarn(
            barn.mapTilResultatBarn(),
            BeregnetForskuddResultat(
                beregnetForskuddPeriodeListe =
                    listOf(
                        ResultatPeriode(
                            periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                            grunnlagsreferanseListe = emptyList(),
                            resultat =
                                ResultatBeregning(
                                    belop = BigDecimal.ZERO,
                                    kode = avslag!!,
                                    regel = "",
                                ),
                        ),
                    ),
            ),
        )

    // TODO: For testing av evnevurdering. Skal fjernes når testing er ferdig
    @OptIn(ExperimentalStdlibApi::class)
    fun beregnBPsLavesteInntektForEvne(behandling: Behandling): BigDecimal {
        val bidragsevneBeregning = BidragsevneBeregning()
        val sjablonListe = hentSjabloner()
        val beregninResultat = beregneSærbidrag(behandling)
        val delberegningBPsBeregnedeTotalbidrag =
            beregninResultat.grunnlagListe.finnDelberegningBPsBeregnedeTotalbidrag(
                beregninResultat.beregnetSærbidragPeriodeListe.first().grunnlagsreferanseListe,
            )
        var low = 0
        var high = 1000000000
        var result = BigDecimal.ZERO
        val sumLøpendeBidrag = delberegningBPsBeregnedeTotalbidrag!!.bidragspliktigesBeregnedeTotalbidrag.setScale(0, RoundingMode.HALF_UP)
        while (low <= high) {
            val inntektBeløp = (low + high) / 2
            val antallBarnIHusstand =
                behandling.husstandsmedlem
                    .sumOf {
                        it.perioder
                            .count { it.bostatus == Bostatuskode.DELT_BOSTED }
                            .toBigDecimal()
                            .divide(BigDecimal.TWO) +
                            it.perioder.count { it.bostatus == Bostatuskode.MED_FORELDER }.toBigDecimal()
                    }
            val antallVoksneIHustand =
                behandling.husstandsmedlem
                    .filter {
                        it.perioder.any {
                            it.bostatus ==
                                Bostatuskode.BOR_MED_ANDRE_VOKSNE
                        }
                    }.count()

            val mapper = object : CoreMapper() {}
            val sjablontallMap = HashMap<String, SjablonTallNavn>()
            for (sjablonTallNavn in SjablonTallNavn.entries) {
                sjablontallMap[sjablonTallNavn.id] = sjablonTallNavn
            }
            val sjablonPeriodeCoreListe = ArrayList<SjablonPeriodeCore>()

            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonSjablontall(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonSjablontallListe = sjablonListe.sjablonSjablontallResponse,
                    sjablontallMap = sjablontallMap,
                    criteria = { it.bidragsevne },
                ),
            )
            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonBidragsevne(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonBidragsevneListe = sjablonListe.sjablonBidragsevneResponse,
                ),
            )
            sjablonPeriodeCoreListe.addAll(
                mapper.mapSjablonTrinnvisSkattesats(
                    beregnDatoFra = behandling.virkningstidspunkt!!,
                    beregnDatoTil = behandling.virkningstidspunkt!!.plusMonths(1),
                    sjablonTrinnvisSkattesatsListe = sjablonListe.sjablonTrinnvisSkattesatsResponse,
                ),
            )
            val resultatBPsEvne =
                bidragsevneBeregning.beregn(
                    GrunnlagBeregning(
                        inntekt =
                            no.nav.bidrag.beregn.særbidrag.core.bidragsevne.bo
                                .Inntekt("", inntektBeløp.toBigDecimal()),
                        antallBarnIHusstand = AntallBarnIHusstand("", antallBarnIHusstand.toDouble()),
                        bostatusVoksneIHusstand = BostatusVoksneIHusstand("", antallVoksneIHustand != 0),
                        sjablonListe = mapSjablonPeriodeListe(sjablonPeriodeCoreListe),
                    ),
                )
            val beregnetBPsEvne = resultatBPsEvne.beløp.setScale(0, RoundingMode.HALF_UP)
            if (sumLøpendeBidrag == BigDecimal.ZERO) {
                if (beregnetBPsEvne > BigDecimal.ZERO) {
                    result = inntektBeløp.toBigDecimal()
                    high = inntektBeløp - 1
                } else {
                    low = inntektBeløp + 1
                }
            } else {
                if (beregnetBPsEvne == (sumLøpendeBidrag + BigDecimal.ONE)) {
                    result = inntektBeløp.toBigDecimal()
                    high = inntektBeløp - 1
                } else if (beregnetBPsEvne <= sumLøpendeBidrag) {
                    low = inntektBeløp + 1
                } else {
                    high = inntektBeløp - 1
                }
            }
        }
        return result
    }

    protected fun mapSjablonPeriodeListe(sjablonPeriodeListeCore: List<SjablonPeriodeCore>): List<SjablonPeriode> {
        val sjablonPeriodeListe = mutableListOf<SjablonPeriode>()
        sjablonPeriodeListeCore.forEach {
            val sjablonNøkkelListe = mutableListOf<SjablonNøkkel>()
            val sjablonInnholdListe = mutableListOf<SjablonInnhold>()
            it.nøkkelListe!!.forEach { nøkkel ->
                sjablonNøkkelListe.add(SjablonNøkkel(navn = nøkkel.navn, verdi = nøkkel.verdi))
            }
            it.innholdListe.forEach { innhold ->
                sjablonInnholdListe.add(SjablonInnhold(navn = innhold.navn, verdi = innhold.verdi))
            }
            sjablonPeriodeListe.add(
                SjablonPeriode(
                    sjablonPeriode = Periode(datoFom = it.periode.datoFom, datoTil = it.periode.datoTil),
                    sjablon = Sjablon(navn = it.navn, nøkkelListe = sjablonNøkkelListe, innholdListe = sjablonInnholdListe),
                ),
            )
        }
        return sjablonPeriodeListe
    }

    private fun hentSjabloner(): SjablonListe {
        // Henter sjabloner for sjablontall
        val sjablontallListe = SjablonProvider.hentSjablontall()

        // Henter sjabloner for bidragsevne
        val sjablonBidragsevneListe = SjablonProvider.hentSjablonBidragsevne()

        // Henter sjabloner for samværsfradrag
        val sjablonSamværsfradragListe = SjablonProvider.hentSjablonSamværsfradrag()

        // Henter sjabloner for trinnvis skattesats
        val sjablonTrinnvisSkattesatsListe = SjablonProvider.hentSjablonTrinnvisSkattesats()

        return SjablonListe(
            sjablonSjablontallResponse = sjablontallListe,
            sjablonBidragsevneResponse = sjablonBidragsevneListe,
            sjablonTrinnvisSkattesatsResponse = sjablonTrinnvisSkattesatsListe,
            sjablonSamværsfradragResponse = sjablonSamværsfradragListe,
        )
    }
}
