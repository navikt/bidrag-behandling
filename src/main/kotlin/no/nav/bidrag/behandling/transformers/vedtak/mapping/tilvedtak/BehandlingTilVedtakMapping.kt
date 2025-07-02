package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.opprettUnikReferanse
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.service.BeregningService
import no.nav.bidrag.behandling.transformers.finnAldersjusteringDetaljerGrunnlag
import no.nav.bidrag.behandling.transformers.finnAldersjusteringDetaljerReferanse
import no.nav.bidrag.behandling.transformers.finnIndeksår
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.hentRolleMedFnr
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.totalBeløpBetaltAvBp
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonMedIdent
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.behandling.transformers.vedtak.reelMottakerEllerBidragsmottaker
import no.nav.bidrag.behandling.transformers.vedtak.tilVedtakDto
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettEngangsbeløpRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettStønadsendringRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.sak.BidragssakDto
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

@Service
@Import(BeregnGebyrApi::class)
class BehandlingTilVedtakMapping(
    private val sakConsumer: BidragSakConsumer,
    private val mapper: VedtakGrunnlagMapper,
    private val beregningService: BeregningService,
    private val vedtaksconsumer: BidragVedtakConsumer,
    private val vedtakService: no.nav.bidrag.beregn.barnebidrag.service.VedtakService,
) {
    fun Behandling.byggOpprettVedtakRequestBidragAldersjustering(enhet: String? = null): OpprettVedtakRequestDto {
        val sak = sakConsumer.hentSak(saksnummer)
        val beregning = beregningService.beregneBidrag(id!!)
        if (beregning.any { it.ugyldigBeregning != null }) {
            val begrunnelse = beregning.filter { it.ugyldigBeregning != null }.joinToString { it.ugyldigBeregning!!.begrunnelse }
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke fatte vedtak: $begrunnelse",
            )
        }
        // Ønsker ikke virkningstidspunkt grunnlag fra aldersjusteringen
        val beregningGrunnlagsliste =
            beregning
                .first()
                .resultat.grunnlagListe
                .filter { it.type != Grunnlagstype.VIRKNINGSTIDSPUNKT }

        val bidragspliktigGrunnlag = beregningGrunnlagsliste.bidragspliktig ?: bidragspliktig!!.tilGrunnlagPerson()
        val bidragsmottakerGrunnlag = beregningGrunnlagsliste.bidragsmottaker ?: bidragsmottaker!!.tilGrunnlagPerson()
        val grunnlagPersoner =
            setOf(
                bidragspliktigGrunnlag,
                bidragsmottakerGrunnlag,
            ).map { it.tilOpprettRequestDto() }
        val grunnlagManuelleVedtak = byggGrunnlagManuelleVedtak(beregningGrunnlagsliste).map { it.tilOpprettRequestDto() }
        val stønadsendringGrunnlag = byggGrunnlagVirkningsttidspunkt(beregningGrunnlagsliste).map { it.tilOpprettRequestDto() }
        val grunnlagsliste =
            beregningGrunnlagsliste.map { it.tilOpprettRequestDto() } + stønadsendringGrunnlag + grunnlagManuelleVedtak + grunnlagPersoner

        val aldersjusteringGrunnlag = beregningGrunnlagsliste.finnAldersjusteringDetaljerGrunnlag()

        val erAldersjustert = aldersjusteringGrunnlag?.aldersjustert ?: false

        return byggOpprettVedtakRequestObjekt(enhet)
            .copy(
                grunnlagListe = grunnlagsliste.toHashSet().toList(),
                stønadsendringListe =
                    beregning.map {
                        val søknadsbarnRolle = søknadsbarn.find { sb -> sb.ident == it.barn.ident!!.verdi }!!
                        val søknadsbarnGrunnlag =
                            grunnlagsliste.toSet().hentPersonMedIdent(søknadsbarnRolle.ident) ?: søknadsbarnRolle.tilGrunnlagPerson()
                        val stønad = tilStønadsid(søknadsbarnRolle)
                        val perioder =
                            it.resultat.beregnetBarnebidragPeriodeListe.map {
                                OpprettPeriodeRequestDto(
                                    periode = it.periode,
                                    beløp = it.resultat.beløp,
                                    valutakode = "NOK",
                                    resultatkode = Resultatkode.BEREGNET_BIDRAG.name,
                                    grunnlagReferanseListe = it.grunnlagsreferanseListe,
                                )
                            }
                        val opphørPeriode =
                            listOfNotNull(opprettPeriodeOpphør(søknadsbarnRolle, perioder, TypeBehandling.BIDRAG))
                        val grunnlagManuelleVedtakBarn =
                            grunnlagManuelleVedtak.filter {
                                it.gjelderBarnReferanse ==
                                    søknadsbarnGrunnlag.referanse
                            }
                        OpprettStønadsendringRequestDto(
                            type = stønad.type,
                            sak = stønad.sak,
                            kravhaver = stønad.kravhaver,
                            skyldner = stønad.skyldner,
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(søknadsbarnRolle.ident!!),
                                    ),
                            beslutning = if (erAldersjustert) Beslutningstype.ENDRING else Beslutningstype.AVVIST,
                            grunnlagReferanseListe =
                                listOfNotNull(beregningGrunnlagsliste.finnAldersjusteringDetaljerReferanse()) +
                                    stønadsendringGrunnlag.map { it.referanse } + grunnlagManuelleVedtakBarn.map { it.referanse },
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            sisteVedtaksid = vedtakService.finnSisteVedtaksid(stønad),
                            førsteIndeksreguleringsår =
                                if (erAldersjustert) {
                                    YearMonth.now().year + 1
                                } else {
                                    null
                                },
                            periodeListe = perioder + opphørPeriode,
                        )
                    },
            )
    }

    fun Behandling.byggOpprettVedtakRequestBidragAlle(enhet: String? = null): OpprettVedtakRequestDto {
        return if (vedtakstype == Vedtakstype.ALDERSJUSTERING) {
            return byggOpprettVedtakRequestBidragAldersjustering(enhet)
        } else {
            byggOpprettVedtakRequestBidrag(enhet)
        }
    }

    private fun Behandling.byggOpprettVedtakRequestBidrag(enhet: String? = null): OpprettVedtakRequestDto {
        val behandling = this
        val sak = sakConsumer.hentSak(saksnummer)
        val beregning = beregningService.beregneBidrag(id!!)

        if (beregning.any { it.ugyldigBeregning != null }) {
            val begrunnelse = beregning.filter { it.ugyldigBeregning != null }.joinToString { it.ugyldigBeregning!!.begrunnelse }
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Kan ikke fatte vedtak: $begrunnelse",
            )
        }

        mapper.run {
            val stønadsendringPerioder =
                beregning.map { it.byggStønadsendringerForVedtak(behandling) }
            val stønadsendringGrunnlag = stønadsendringPerioder.flatMap(StønadsendringPeriode::grunnlag)

            val grunnlagListeVedtak =
                byggGrunnlagForVedtak(stønadsendringGrunnlag.hentAllePersoner().toMutableSet() as MutableSet<GrunnlagDto>)
            val stønadsendringGrunnlagListe = byggGrunnlagGenerelt()

            val grunnlagListe =
                (grunnlagListeVedtak + stønadsendringGrunnlag + stønadsendringGrunnlagListe).toSet()
            val engangsbeløpGebyr = mapEngangsbeløpGebyr(grunnlagListe.toList())
            val grunnlagVirkningstidspunkt = byggGrunnlagVirkningsttidspunkt()

            return byggOpprettVedtakRequestObjekt(enhet).copy(
                stønadsendringListe =
                    stønadsendringPerioder.map {
                        val sistePeriode = it.perioder.maxBy { it.periode.fom }.periode
                        val søknadsbarnReferanse = it.barn.tilGrunnlagsreferanse()
                        OpprettStønadsendringRequestDto(
                            innkreving = innkrevingstype!!,
                            skyldner = tilSkyldner(),
                            omgjørVedtakId = refVedtaksid?.toInt(),
                            kravhaver =
                                it.barn.tilNyestePersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, id!!),
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(it.barn.ident!!),
                                    ),
                            sak = Saksnummer(saksnummer),
                            type = stonadstype!!,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe =
                                stønadsendringGrunnlagListe.map(GrunnlagDto::referanse) +
                                    grunnlagVirkningstidspunkt
                                        .find { vt ->
                                            vt.gjelderBarnReferanse == it.barn.tilGrunnlagsreferanse()
                                        }!!
                                        .referanse,
                            periodeListe = it.perioder,
                            førsteIndeksreguleringsår =
                                grunnlagListe.toList().finnIndeksår(søknadsbarnReferanse, sistePeriode),
                        )
                    },
                engangsbeløpListe =
                    engangsbeløpGebyr.engangsbeløp + mapEngangsbeløpDirekteOppgjør(sak),
                grunnlagListe =
                    (grunnlagListe + engangsbeløpGebyr.grunnlagsliste + grunnlagVirkningstidspunkt).toSet().map(
                        BaseGrunnlag::tilOpprettRequestDto,
                    ),
            )
        }
    }

    private fun Behandling.byggGrunnlagForGebyr(): Set<GrunnlagDto> = byggGrunnlagManueltOverstyrtGebyr()

    private fun Behandling.mapEngangsbeløpGebyr(grunnlagsliste: List<GrunnlagDto>): GebyrResulat {
        val gebyrGrunnlagsliste: MutableSet<BaseGrunnlag> = mutableSetOf()
        val grunnlagslisteGebyr = grunnlagsliste + byggGrunnlagForGebyr()
        val engangsbeløpListe =
            listOfNotNull(
                bidragspliktig!!.harGebyrsøknad.ifTrue {
                    val beregning = mapper.beregnGebyr(this, bidragspliktig!!, grunnlagslisteGebyr)
                    gebyrGrunnlagsliste.addAll(beregning.grunnlagsliste)
                    val ilagtGebyr = beregning.ilagtGebyr
                    val skyldner = Personident(bidragspliktig!!.ident!!)
                    OpprettEngangsbeløpRequestDto(
                        type = Engangsbeløptype.GEBYR_SKYLDNER,
                        beløp = if (ilagtGebyr) beregning.beløpGebyrsats else null,
                        betaltBeløp = null,
                        resultatkode = beregning.resultatkode.name,
                        eksternReferanse = null,
                        referanse = hentUnikReferanseEngangsbeløp(personIdentNav, Engangsbeløptype.GEBYR_SKYLDNER, skyldner),
                        beslutning = Beslutningstype.ENDRING,
                        grunnlagReferanseListe = beregning.grunnlagsreferanseListeEngangsbeløp,
                        innkreving = Innkrevingstype.MED_INNKREVING,
                        skyldner = skyldner,
                        kravhaver = personIdentNav,
                        mottaker = personIdentNav,
                        valutakode = if (ilagtGebyr) "NOK" else null,
                        sak = Saksnummer(saksnummer),
                    )
                },
                bidragsmottaker!!.harGebyrsøknad.ifTrue {
                    val beregning = mapper.beregnGebyr(this, bidragsmottaker!!, grunnlagslisteGebyr)
                    gebyrGrunnlagsliste.addAll(beregning.grunnlagsliste)
                    val ilagtGebyr = beregning.ilagtGebyr
                    val skyldner = Personident(bidragsmottaker!!.ident!!)
                    OpprettEngangsbeløpRequestDto(
                        type = Engangsbeløptype.GEBYR_MOTTAKER,
                        beløp = if (ilagtGebyr) beregning.beløpGebyrsats else null,
                        betaltBeløp = null,
                        resultatkode = beregning.resultatkode.name,
                        referanse = hentUnikReferanseEngangsbeløp(personIdentNav, Engangsbeløptype.GEBYR_MOTTAKER, skyldner),
                        eksternReferanse = null,
                        beslutning = Beslutningstype.ENDRING,
                        grunnlagReferanseListe = beregning.grunnlagsreferanseListeEngangsbeløp,
                        innkreving = Innkrevingstype.MED_INNKREVING,
                        skyldner = skyldner,
                        kravhaver = personIdentNav,
                        mottaker = personIdentNav,
                        valutakode = if (ilagtGebyr) "NOK" else null,
                        sak = Saksnummer(saksnummer),
                    )
                },
            )
        return GebyrResulat(engangsbeløpListe, gebyrGrunnlagsliste)
    }

    private fun Behandling.mapEngangsbeløpDirekteOppgjør(sak: BidragssakDto) =
        søknadsbarn
            .filter {
                it.innbetaltBeløp != null &&
                    it.innbetaltBeløp!! > BigDecimal.ZERO
            }.map {
                mapper.run {
                    val kravhaver = it.tilNyestePersonident() ?: rolleManglerIdent(Rolletype.BARN, id!!)
                    OpprettEngangsbeløpRequestDto(
                        type = Engangsbeløptype.DIREKTE_OPPGJØR,
                        beløp = it.innbetaltBeløp,
                        betaltBeløp = null,
                        resultatkode = Resultatkode.DIREKTE_OPPGJØR.name,
                        eksternReferanse = null,
                        beslutning = Beslutningstype.ENDRING,
                        grunnlagReferanseListe = emptyList(),
                        referanse = hentUnikReferanseEngangsbeløp(kravhaver, Engangsbeløptype.DIREKTE_OPPGJØR),
                        innkreving = innkrevingstype!!,
                        skyldner = tilSkyldner(),
                        kravhaver =
                            it.tilNyestePersonident()
                                ?: rolleManglerIdent(Rolletype.BARN, id!!),
                        mottaker =
                            roller
                                .reelMottakerEllerBidragsmottaker(
                                    sak.hentRolleMedFnr(it.ident!!),
                                ),
                        valutakode = "NOK",
                        omgjørVedtakId = refVedtaksid?.toInt(),
                        sak = Saksnummer(saksnummer),
                    )
                }
            }

    fun Behandling.byggOpprettVedtakRequestAvslagForBidrag(enhet: String? = null): OpprettVedtakRequestDto =
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val grunnlagListe = byggGrunnlagGenereltAvslag()
            val grunnlagslisteGebyr = byggGrunnlagForGebyr()
            val resultatEngangsbeløpGebyr = mapEngangsbeløpGebyr(grunnlagListe.toList() + grunnlagslisteGebyr)
            val grunnlagVirkningstidspunkt = byggGrunnlagVirkningsttidspunkt()

            return byggOpprettVedtakRequestObjekt(enhet)
                .copy(
                    engangsbeløpListe = resultatEngangsbeløpGebyr.engangsbeløp,
                    stønadsendringListe =
                        søknadsbarn.map {
                            OpprettStønadsendringRequestDto(
                                innkreving = innkrevingstype!!,
                                skyldner = tilSkyldner(),
                                omgjørVedtakId = refVedtaksid?.toInt(),
                                kravhaver =
                                    it.tilNyestePersonident()
                                        ?: rolleManglerIdent(Rolletype.BARN, id!!),
                                mottaker =
                                    roller
                                        .reelMottakerEllerBidragsmottaker(
                                            sak.hentRolleMedFnr(it.ident!!),
                                        ),
                                sak = Saksnummer(saksnummer),
                                type = stonadstype!!,
                                beslutning = Beslutningstype.ENDRING,
                                grunnlagReferanseListe =
                                    grunnlagListe.map { it.referanse } +
                                        grunnlagVirkningstidspunkt
                                            .find { vt ->
                                                vt.gjelderBarnReferanse == it.tilGrunnlagsreferanse()
                                            }!!
                                            .referanse,
                                periodeListe =
                                    listOf(
                                        OpprettPeriodeRequestDto(
                                            periode = ÅrMånedsperiode(virkningstidspunktEllerSøktFomDato, null),
                                            beløp = null,
                                            resultatkode = avslag!!.name,
                                            valutakode = "NOK",
                                            grunnlagReferanseListe = emptyList(),
                                        ),
                                    ),
                            )
                        },
                    grunnlagListe =
                        (grunnlagListe + tilPersonobjekter() + resultatEngangsbeløpGebyr.grunnlagsliste + grunnlagVirkningstidspunkt).map(
                            BaseGrunnlag::tilOpprettRequestDto,
                        ),
                )
        }

    fun Behandling.byggOpprettVedtakRequestAvslagForSærbidrag(enhet: String? = null): OpprettVedtakRequestDto {
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val grunnlagListe = byggGrunnlagGenereltAvslag()
            val barn = søknadsbarn.first()

            val kravhaver = barn.tilNyestePersonident() ?: rolleManglerIdent(Rolletype.BARN, id!!)

            return byggOpprettVedtakRequestObjekt(enhet)
                .copy(
                    engangsbeløpListe =
                        listOf(
                            OpprettEngangsbeløpRequestDto(
                                type = engangsbeloptype!!,
                                beløp = null,
                                resultatkode = tilSærbidragAvslagskode()!!.name,
                                valutakode = "NOK",
                                betaltBeløp = null,
                                referanse = hentUnikReferanseEngangsbeløp(kravhaver, engangsbeloptype!!),
                                innkreving = innkrevingstype!!,
                                skyldner = tilSkyldner(),
                                omgjørVedtakId = refVedtaksid?.toInt(),
                                kravhaver = kravhaver,
                                mottaker =
                                    roller
                                        .reelMottakerEllerBidragsmottaker(
                                            sak.hentRolleMedFnr(barn.ident!!),
                                        ),
                                sak = Saksnummer(saksnummer),
                                beslutning = Beslutningstype.ENDRING,
                                grunnlagReferanseListe = grunnlagListe.map(GrunnlagDto::referanse),
                            ),
                        ),
                    grunnlagListe = (grunnlagListe + tilPersonobjekter()).map(GrunnlagDto::tilOpprettRequestDto),
                )
        }
    }

    fun Behandling.hentUnikReferanseEngangsbeløp(
        kravhaver: Personident,
        type: Engangsbeløptype,
        skyldner: Personident? = null,
    ) = if (refVedtaksid != null) {
        val vedtak = vedtaksconsumer.hentVedtak(refVedtaksid!!.toInt())!!
        val engangsbeløp =
            vedtak.engangsbeløpListe.find {
                it.type == type &&
                    it.kravhaver == kravhaver &&
                    (skyldner == null || it.skyldner == skyldner)
            }!!
        engangsbeløp.referanse
    } else {
        opprettUnikReferanse(type.name)
    }

    fun Behandling.byggOpprettVedtakRequestSærbidrag(enhet: String? = null): OpprettVedtakRequestDto {
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val beregning = beregningService.beregneSærbidrag(id!!)
            val resultat = beregning.beregnetSærbidragPeriodeListe.first().resultat
            val (grunnlagListeVedtak, grunnlaglisteGenerelt) =
                if (resultat.resultatkode == Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS) {
                    byggGrunnlagForAvslagUgyldigUtgifter()
                } else {
                    val personobjekterFraBeregning = beregning.grunnlagListe.hentAllePersoner().toMutableSet() as MutableSet<GrunnlagDto>
                    listOf(byggGrunnlagForVedtak(personobjekterFraBeregning), byggGrunnlagGenerelt())
                }

            val grunnlagliste = (grunnlagListeVedtak + grunnlaglisteGenerelt + beregning.grunnlagListe).toSet()

            val grunnlagslisteEngangsbeløp =
                grunnlaglisteGenerelt +
                    beregning.grunnlagListe.filter { it.type == Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG }

            val barn = søknadsbarn.first()

            val kravhaver = barn.tilNyestePersonident() ?: rolleManglerIdent(Rolletype.BARN, id!!)
            return byggOpprettVedtakRequestObjekt(enhet).copy(
                engangsbeløpListe =
                    listOf(
                        OpprettEngangsbeløpRequestDto(
                            type = engangsbeloptype!!,
                            beløp = resultat.beløp,
                            resultatkode = resultat.resultatkode.name,
                            valutakode = "NOK",
                            betaltBeløp = utgift!!.totalBeløpBetaltAvBp,
                            innkreving = innkrevingstype!!,
                            skyldner = tilSkyldner(),
                            referanse = hentUnikReferanseEngangsbeløp(kravhaver, engangsbeloptype!!),
                            omgjørVedtakId = refVedtaksid?.toInt(),
                            kravhaver =
                                barn.tilNyestePersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, id!!),
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(barn.ident!!),
                                    ),
                            sak = Saksnummer(saksnummer),
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = grunnlagslisteEngangsbeløp.map(GrunnlagDto::referanse),
                        ),
                    ),
                grunnlagListe = grunnlagliste.map(GrunnlagDto::tilOpprettRequestDto),
            )
        }
    }

    fun Behandling.byggOpprettVedtakRequestForskudd(enhet: String? = null): OpprettVedtakRequestDto {
        val behandling = this
        val sak = sakConsumer.hentSak(saksnummer)
        val beregning = beregningService.beregneForskudd(id!!)

        mapper.run {
            val stønadsendringPerioder =
                beregning.map { it.byggStønadsendringerForVedtak(behandling) }

            val grunnlagListeVedtak = byggGrunnlagForVedtak()
            val stønadsendringGrunnlagListe = byggGrunnlagGenerelt()

            val grunnlagListe =
                (
                    grunnlagListeVedtak +
                        stønadsendringPerioder.flatMap(
                            StønadsendringPeriode::grunnlag,
                        ) + stønadsendringGrunnlagListe
                ).toSet()

            return byggOpprettVedtakRequestObjekt(enhet).copy(
                stønadsendringListe =
                    stønadsendringPerioder.map {
                        val søknadsbarn =
                            behandling.søknadsbarn.find { sb -> sb.ident == it.barn.ident }
                                ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)
                        val opphørPeriode =
                            listOfNotNull(opprettPeriodeOpphør(søknadsbarn, it.perioder, TypeBehandling.FORSKUDD))
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = tilSkyldner(),
                            omgjørVedtakId = refVedtaksid?.toInt(),
                            kravhaver =
                                it.barn.tilNyestePersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, id!!),
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(it.barn.ident!!),
                                    ),
                            sak = Saksnummer(saksnummer),
                            type = stonadstype!!,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = stønadsendringGrunnlagListe.map(GrunnlagDto::referanse),
                            periodeListe = it.perioder + opphørPeriode,
                            // Settes null for forskudd men skal settes til riktig verdi for bidrag
                            førsteIndeksreguleringsår = null,
                        )
                    },
                grunnlagListe = grunnlagListe.map(GrunnlagDto::tilOpprettRequestDto),
            )
        }
    }

    fun Behandling.byggOpprettVedtakRequestAvslagForForskudd(enhet: String? = null): OpprettVedtakRequestDto =
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val grunnlagListe = byggGrunnlagGenereltAvslag()

            return byggOpprettVedtakRequestObjekt(enhet)
                .copy(
                    stønadsendringListe =
                        søknadsbarn.map {
                            OpprettStønadsendringRequestDto(
                                innkreving = Innkrevingstype.MED_INNKREVING,
                                skyldner = tilSkyldner(),
                                omgjørVedtakId = refVedtaksid?.toInt(),
                                kravhaver =
                                    it.tilNyestePersonident()
                                        ?: rolleManglerIdent(Rolletype.BARN, id!!),
                                mottaker =
                                    roller
                                        .reelMottakerEllerBidragsmottaker(
                                            sak.hentRolleMedFnr(it.ident!!),
                                        ),
                                sak = Saksnummer(saksnummer),
                                type = stonadstype!!,
                                beslutning = Beslutningstype.ENDRING,
                                grunnlagReferanseListe = grunnlagListe.map { it.referanse },
                                periodeListe =
                                    listOf(
                                        OpprettPeriodeRequestDto(
                                            periode = ÅrMånedsperiode(virkningstidspunktEllerSøktFomDato, null),
                                            beløp = null,
                                            resultatkode = avslag!!.name,
                                            valutakode = "NOK",
                                            grunnlagReferanseListe = emptyList(),
                                        ),
                                    ),
                            )
                        },
                    grunnlagListe = (grunnlagListe + tilPersonobjekter()).map(GrunnlagDto::tilOpprettRequestDto),
                )
        }

    fun Behandling.mapBehandlingTilVedtakDto(): VedtakDto {
        mapper.validering.run {
            val request =
                when (tilType()) {
                    TypeBehandling.SÆRBIDRAG ->
                        if (erDirekteAvslagUtenBeregning()) {
                            byggOpprettVedtakRequestAvslagForSærbidrag()
                        } else {
                            byggOpprettVedtakRequestSærbidrag()
                        }

                    TypeBehandling.FORSKUDD ->
                        if (avslag != null) {
                            byggOpprettVedtakRequestAvslagForForskudd()
                        } else {
                            byggOpprettVedtakRequestForskudd()
                        }
                    TypeBehandling.BIDRAG ->
                        if (avslag != null) {
                            byggOpprettVedtakRequestAvslagForBidrag()
                        } else {
                            byggOpprettVedtakRequestBidragAlle()
                        }

                    else -> throw HttpClientErrorException(
                        HttpStatus.BAD_REQUEST,
                        "Behandlingstype ${tilType()} støttes ikke",
                    )
                }
            return request.tilVedtakDto()
        }
    }

    private fun Behandling.byggOpprettVedtakRequestObjekt(enhet: String?): OpprettVedtakRequestDto =
        OpprettVedtakRequestDto(
            enhetsnummer = Enhetsnummer(enhet ?: behandlerEnhet),
            vedtakstidspunkt = LocalDateTime.now(),
            type = vedtakstype,
            stønadsendringListe = emptyList(),
            engangsbeløpListe = emptyList(),
            behandlingsreferanseListe = tilBehandlingreferanseListe(),
            grunnlagListe = emptyList(),
            kilde = Vedtakskilde.MANUELT,
            fastsattILand = null,
            unikReferanse = opprettUnikReferanse(null),
            innkrevingUtsattTilDato = null,
            // Settes automatisk av bidrag-vedtak basert på token
            opprettetAv = null,
        )
}
