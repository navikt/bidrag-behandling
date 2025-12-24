package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import disableUnleashFeature
import enableUnleashFeature
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.database.datamodell.opprettUnikReferanse
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.dto.v2.vedtak.OppdaterParagraf35cDetaljerDto
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentNotat
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.lagVedtaksdata
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagEtterfølgendeVedtak
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagManuelleVedtak
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettPrivatAvtale
import no.nav.bidrag.behandling.utils.testdata.opprettPrivatAvtalePeriode
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.BidragsberegningOrkestrator
import no.nav.bidrag.beregn.barnebidrag.utils.tilDto
import no.nav.bidrag.beregn.barnebidrag.utils.toYearMonth
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorResponseV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningResultatBarnV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtakV2
import no.nav.bidrag.transport.behandling.felles.grunnlag.EtterfølgendeManuelleVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtaleGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtalePeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjektListe
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.finnOrkestreringDetaljer
import no.nav.bidrag.transport.felles.toCompactString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import kotlin.collections.first

@ExtendWith(SpringExtension::class)
class VedtakserviceBidragKlageTest : CommonVedtakTilBehandlingTest() {
    @BeforeEach
    fun initMocksKlage() {
        enableUnleashFeature(UnleashFeatures.FATTE_VEDTAK)
        bidragsberegningOrkestrator = mockkClass(BidragsberegningOrkestrator::class)
        behandlingService =
            BehandlingService(
                behandlingRepository,
                mockkClass(ForsendelseService::class),
                virkningstidspunktService,
                tilgangskontrollService,
                grunnlagService,
                dtomapper,
                validerBehandlingService,
                underholdService,
            )
        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
                aldersjusteringOrchestrator,
                bidragsberegningOrkestrator,
            )
        behandlingTilVedtakMapping =
            BehandlingTilVedtakMapping(
                sakConsumer,
                vedtakGrunnlagMapper,
                beregningService,
                vedtakConsumer,
                vedtakServiceBeregning,
            )

        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                tilgangskontrollService,
                vedtakConsumer,
                validerBeregning,
                vedtakTilBehandlingMapping,
                behandlingTilVedtakMapping,
                validerBehandlingService,
                forsendelseService,
                virkningstidspunktService,
            )
    }

    private fun byggGrunnlagForBeregning(
        behandling: Behandling,
        søknadsbarn: Rolle,
        endeligBeregning: Boolean = false,
    ) = vedtakGrunnlagMapper.byggGrunnlagForBeregning(behandling, søknadsbarn, endeligBeregning).beregnGrunnlag.grunnlagListe

    @Test
    fun `Skal ikke fatte vedtak for klage hvis feature skrudd av`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(søknadsbarn.tilGrunnlagPerson()),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        disableUnleashFeature(UnleashFeatures.FATTE_VEDTAK)
        assertThrows<HttpClientErrorException> { vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null)) }
    }

    @Test
    fun `Skal fatte vedtak for klage`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagEtterfølgendeVedtak()

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(søknadsbarn.tilGrunnlagPerson()),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode =
                                                ÅrMånedsperiode(
                                                    behandling.virkningstidspunkt!!,
                                                    behandling.finnBeregnTilDatoBehandling(søknadsbarn),
                                                ),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.finnBeregnTilDatoBehandling(søknadsbarn)),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        val opprettVedtakRequest = opprettVedtakSlot.first()

        opprettVedtakSlot shouldHaveSize 1
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 29
            }
            request.unikReferanse shouldBe behandling.opprettUnikReferanse("omgjøring")
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            stønadsendring.omgjørVedtakId shouldBe 2
            stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
            val grunnlagVirkning =
                opprettVedtakRequest.grunnlagListe
                    .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                        no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.VIRKNINGSTIDSPUNKT,
                        stønadsendring.grunnlagReferanseListe,
                    ).firstOrNull()
            grunnlagVirkning.shouldNotBeNull()
            grunnlagVirkning.innhold.virkningstidspunkt shouldBe søknadsbarn.virkningstidspunkt
            grunnlagVirkning.innhold.beregnTil shouldBe søknadsbarn.beregnTil
            grunnlagVirkning.innhold.beregnTilDato shouldBe behandling.finnBeregnTilDatoBehandling(søknadsbarn).toYearMonth()

            val grunnlagEtterfølgendeVedtak =
                opprettVedtakRequest.grunnlagListe
                    .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                        no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                        stønadsendring.grunnlagReferanseListe,
                    ).firstOrNull()
            grunnlagEtterfølgendeVedtak.shouldNotBeNull()
            grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

            val grunnlagManuelleVedtak =
                opprettVedtakRequest.grunnlagListe
                    .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                    .firstOrNull()
                    ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
            grunnlagManuelleVedtak.shouldNotBeNull()
            grunnlagManuelleVedtak.shouldHaveSize(1)
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage med opphør`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opphørsdato = LocalDate.parse("2999-08-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(søknadsbarn.tilGrunnlagPerson()),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        val opprettVedtakRequest = opprettVedtakSlot.first()

        opprettVedtakSlot shouldHaveSize 1
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 28
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            stønadsendring.omgjørVedtakId shouldBe 2
            stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

            val perioder = stønadsendring.periodeListe
            perioder.shouldHaveSize(2)

            val periodeOpphør = stønadsendring.periodeListe[1]
            periodeOpphør.periode.fom.shouldBe(søknadsbarn.opphørsdato!!.toYearMonth())
            periodeOpphør.periode.til.shouldBeNull()
            periodeOpphør.beløp.shouldBeNull()
            periodeOpphør.resultatkode shouldBe Resultatkode.OPPHØR.name
            val grunnlagVirkning =
                opprettVedtakRequest.grunnlagListe
                    .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                        no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.VIRKNINGSTIDSPUNKT,
                        periodeOpphør.grunnlagReferanseListe,
                    ).firstOrNull()

            grunnlagVirkning.shouldNotBeNull()
            grunnlagVirkning.innhold.opphørsdato shouldBe søknadsbarn.opphørsdato
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage uten innkreving`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)
        behandling.leggTilNotat("Begrunnelse virkningstidspunkt", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, true)
        behandling.leggTilNotat("Begrunnelse virkningstidspunkt fra opprinnelig vedtak", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, false)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        val vedtaksidKlage = 1
        val vedtaksidinnkreving = 5

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INNKREVING -> vedtaksidinnkreving
                    arg.type == Vedtakstype.KLAGE -> vedtaksidKlage
                    else -> -1
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(søknadsbarn.tilGrunnlagPerson()),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(2), null),
                    beløp = BigDecimal("2000"),
                ),
            ),
        )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 2
        assertSoftly(opprettVedtakSlot[0]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 30
            }

            assertSoftly(it.stønadsendringListe) { se ->
                se.shouldHaveSize(1)
                val stønadsendring = it.stønadsendringListe.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INNKREVING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 8
            }
            val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(2)
                assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt"
                }

                assertSoftly(
                    hentNotat(
                        NotatType.VIRKNINGSTIDSPUNKT,
                        gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
                        fraOmgjortVedtak = true,
                    ),
                ) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt fra opprinnelig vedtak"
                }
            }
            assertSoftly(it.stønadsendringListe) { se ->
                se.shouldHaveSize(1)
                val stønadsendring = it.stønadsendringListe.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe shouldHaveSize 1
                val grunnlagVirkning =
                    opprettVedtakSlot[1]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.virkningstidspunkt shouldBe søknadsbarn.virkningstidspunkt
                grunnlagVirkning.innhold.beregnTil shouldBe søknadsbarn.beregnTil
                grunnlagVirkning.innhold.beregnTilDato shouldBe behandling.finnBeregnTilDatoBehandling(søknadsbarn).toYearMonth()
                val førstePeriode = stønadsendring.periodeListe.first()
                val resultatFraVedtak1 =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            førstePeriode.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtak1.shouldNotBeNull()
                resultatFraVedtak1.innhold.vedtaksid shouldBe vedtaksidKlage
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        verify(exactly = 2) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage uten innkreving med opphør`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opphørsdato = LocalDate.parse("2999-08-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )

        val vedtaksidKlage = 1
        val vedtaksidinnkreving = 5

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INNKREVING -> vedtaksidinnkreving
                    arg.type == Vedtakstype.KLAGE -> vedtaksidKlage
                    else -> -1
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(søknadsbarn.tilGrunnlagPerson()),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(2), null),
                    beløp = BigDecimal("2000"),
                ),
            ),
        )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 2
        assertSoftly(opprettVedtakSlot[0]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 28
            }

            assertSoftly(it.stønadsendringListe) { se ->
                se.shouldHaveSize(1)
                val stønadsendring = it.stønadsendringListe.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INNKREVING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 6
            }

            assertSoftly(it.stønadsendringListe) { se ->
                se.shouldHaveSize(1)
                val stønadsendring = it.stønadsendringListe.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe shouldHaveSize 2
                val førstePeriode = stønadsendring.periodeListe.first()
                val resultatFraVedtak1 =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            førstePeriode.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtak1.shouldNotBeNull()
                resultatFraVedtak1.innhold.vedtaksid shouldBe vedtaksidKlage

                val periodeOpphør = stønadsendring.periodeListe.last()
                periodeOpphør.periode.fom.shouldBe(søknadsbarn.opphørsdato!!.toYearMonth())
                periodeOpphør.periode.til.shouldBeNull()
                periodeOpphør.beløp.shouldBeNull()
                periodeOpphør.resultatkode shouldBe Resultatkode.OPPHØR.name
                val grunnlagVirkning =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            periodeOpphør.grunnlagReferanseListe,
                        ).firstOrNull()

                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.opphørsdato shouldBe søknadsbarn.opphørsdato
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        verify(exactly = 2) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage med orkestrering`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)
        behandling.leggTilNotat("Begrunnelse virkningstidspunkt", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, true)
        behandling.leggTilNotat("Begrunnelse inntekt BP", NotatType.INNTEKT, behandling.bidragspliktig, true)
        behandling.leggTilNotat("Begrunnelse inntekt BM", NotatType.INNTEKT, behandling.bidragsmottaker, true)
        behandling.leggTilNotat("Begrunnelse inntekt BA", NotatType.INNTEKT, søknadsbarn, true)
        behandling.leggTilNotat("Begrunnelse underhold", NotatType.UNDERHOLDSKOSTNAD, søknadsbarn, true)
        behandling.leggTilNotat("Begrunnelse underhold BM", NotatType.UNDERHOLDSKOSTNAD, behandling.bidragsmottaker, true)
        behandling.leggTilNotat("Begrunnelse samvær", NotatType.SAMVÆR, søknadsbarn, true)
        behandling.leggTilNotat("Begrunnelse boforhold", NotatType.BOFORHOLD, behandling.bidragspliktig, true)
        behandling.leggTilNotat("Begrunnelse privat avtale", NotatType.PRIVAT_AVTALE, søknadsbarn, true)

        behandling.leggTilNotat("Begrunnelse virkningstidspunkt fra opprinnelig vedtak", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, false)
        behandling.leggTilNotat("Begrunnelse inntekt BM fra opprinnelig vedtak", NotatType.INNTEKT, behandling.bidragsmottaker, false)
        behandling.leggTilNotat("Begrunnelse underhold fra opprinnelig vedtak", NotatType.UNDERHOLDSKOSTNAD, søknadsbarn, false)
        behandling.leggTilNotat("Begrunnelse underhold BM fra opprinnelig vedtak", NotatType.UNDERHOLDSKOSTNAD, behandling.bidragsmottaker, false)
        behandling.leggTilNotat("Begrunnelse samvær fra opprinnelig vedtak", NotatType.SAMVÆR, søknadsbarn, false)
        behandling.leggTilNotat("Begrunnelse boforhold fra opprinnelig vedtak", NotatType.BOFORHOLD, behandling.bidragspliktig, false)
        behandling.leggTilNotat("Begrunnelse privat avtale fra opprinnelig vedtak", NotatType.PRIVAT_AVTALE, søknadsbarn, false)

        behandling.leggTilGrunnlagManuelleVedtak()
        behandling.leggTilGrunnlagEtterfølgendeVedtak()
        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.KLAGE && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(
                    søknadsbarn.tilGrunnlagPerson(),
                ),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.INDEKSREGULERING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                grunnlagslisteDelvedtak =
                                    listOf(
                                        GrunnlagDto(
                                            type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                            innhold =
                                                POJONode(
                                                    ResultatFraVedtakGrunnlag(
                                                        vedtaksid = vedtakidsEtterfølgende,
                                                        omgjøringsvedtak = false,
                                                        beregnet = false,
                                                        vedtakstype = Vedtakstype.ENDRING,
                                                        opprettParagraf35c = false,
                                                    ),
                                                ),
                                            referanse = "",
                                        ),
                                    ),
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2025-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-08-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 44
            }
            hentGrunnlagstyper(Grunnlagstype.NOTAT) shouldHaveSize 16
            validerNotater()
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[0].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }
        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INDEKSREGULERING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 14
            }
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[1].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe null
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 11
            }

            opprettVedtakSlot[2].unikReferanse shouldBe
                behandling.opprettUnikReferanse("endeligvedtak")

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2025-03")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage
            orkestreringsdetaljer.innkrevesFraDato.shouldBeNull()

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                val grunnlagVirkning =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.virkningstidspunkt shouldBe søknadsbarn.virkningstidspunkt
                grunnlagVirkning.innhold.beregnTil shouldBe søknadsbarn.beregnTil
                grunnlagVirkning.innhold.beregnTilDato shouldBe behandling.finnBeregnTilDatoBehandling(søknadsbarn).toYearMonth()

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.omgjøringsvedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true
                resultatFraVedtakKlage.innhold.vedtakstype shouldBe Vedtakstype.KLAGE

                val periodeIndeks = stønadsendring.periodeListe[1]
                val resultatFraVedtakIndeks =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeIndeks.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakIndeks.shouldNotBeNull()
                resultatFraVedtakIndeks.innhold.vedtaksid shouldBe vedtaksidIndeks
                resultatFraVedtakIndeks.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakIndeks.innhold.beregnet shouldBe true
                resultatFraVedtakIndeks.innhold.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING

                val periodeEtterfølgende = stønadsendring.periodeListe[2]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
                resultatFraVedtakEtterfølgende.innhold.vedtakstype shouldBe Vedtakstype.ENDRING

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }

        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetVedtak shouldHaveSize 2
            val klagevedtak = it.fattetVedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val indeksreg = it.fattetVedtak.find { it.vedtaksid == vedtaksidIndeks }
            indeksreg.shouldNotBeNull()
            indeksreg.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage med orkestrering med privat avtale`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)
        behandling.leggTilGrunnlagManuelleVedtak()
        behandling.leggTilGrunnlagEtterfølgendeVedtak()
        behandling.leggTilNotat("testtest", NotatType.PRIVAT_AVTALE, søknadsbarn)
        val privatAvtale = opprettPrivatAvtale(behandling, testdataBarn1)
        privatAvtale.skalIndeksreguleres = false
        privatAvtale.perioder.addAll(
            listOf(
                opprettPrivatAvtalePeriode(
                    privatAvtale,
                    fom = YearMonth.from(behandling.virkningstidspunkt),
                    tom = YearMonth.from(behandling.virkningstidspunkt).plusMonths(7),
                ),
                opprettPrivatAvtalePeriode(
                    privatAvtale,
                    fom = YearMonth.from(behandling.virkningstidspunkt).plusMonths(8),
                    tom = null,
                ),
            ),
        )
        behandling.privatAvtale.add(privatAvtale)
        val privaavtaleGrunnlag = behandlingTilGrunnlagMappingV2.run { behandling.tilPrivatAvtaleGrunnlag(behandling.tilPersonobjekter(), privatAvtale.rolle!!.ident!!) }

        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.KLAGE && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                privaavtaleGrunnlag.toList() +
                    listOf(
                        søknadsbarn.tilGrunnlagPerson(),
                    ),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.INDEKSREGULERING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                grunnlagslisteDelvedtak =
                                    listOf(
                                        GrunnlagDto(
                                            type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                            innhold =
                                                POJONode(
                                                    ResultatFraVedtakGrunnlag(
                                                        vedtaksid = vedtakidsEtterfølgende,
                                                        omgjøringsvedtak = false,
                                                        beregnet = false,
                                                        vedtakstype = Vedtakstype.ENDRING,
                                                        opprettParagraf35c = false,
                                                    ),
                                                ),
                                            referanse = "",
                                        ),
                                    ),
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2025-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-08-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 33
            }
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[0].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )
            val privatGrunnlag = it.grunnlagListe.first { it.type == Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG }
            val innholdPrivat = privatGrunnlag.innholdTilObjekt<PrivatAvtaleGrunnlag>()
            innholdPrivat.avtaleInngåttDato shouldBe LocalDate.parse("2024-01-01")
            innholdPrivat.skalIndeksreguleres shouldBe false
            privatGrunnlag.gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            privatGrunnlag.gjelderBarnReferanse shouldBe søknadsbarn.tilGrunnlagsreferanse()

            val privatPeriodeGrunnlag = it.grunnlagListe.filter { it.type == Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG }
            val innholdPrivatPerioder = privatPeriodeGrunnlag.innholdTilObjekt<PrivatAvtalePeriodeGrunnlag>()
            innholdPrivatPerioder.shouldHaveSize(2)
            innholdPrivatPerioder.first().beløp shouldBe BigDecimal(1000)

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }
        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INDEKSREGULERING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 14
            }
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[1].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe null
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 11
            }

            opprettVedtakSlot[2].unikReferanse shouldBe
                behandling.opprettUnikReferanse("endeligvedtak")

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2025-03")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage
            orkestreringsdetaljer.innkrevesFraDato.shouldBeNull()

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                val grunnlagVirkning =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.virkningstidspunkt shouldBe søknadsbarn.virkningstidspunkt
                grunnlagVirkning.innhold.beregnTil shouldBe søknadsbarn.beregnTil
                grunnlagVirkning.innhold.beregnTilDato shouldBe behandling.finnBeregnTilDatoBehandling(søknadsbarn).toYearMonth()

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.omgjøringsvedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true
                resultatFraVedtakKlage.innhold.vedtakstype shouldBe Vedtakstype.KLAGE

                val periodeIndeks = stønadsendring.periodeListe[1]
                val resultatFraVedtakIndeks =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeIndeks.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakIndeks.shouldNotBeNull()
                resultatFraVedtakIndeks.innhold.vedtaksid shouldBe vedtaksidIndeks
                resultatFraVedtakIndeks.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakIndeks.innhold.beregnet shouldBe true
                resultatFraVedtakIndeks.innhold.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING

                val periodeEtterfølgende = stønadsendring.periodeListe[2]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
                resultatFraVedtakEtterfølgende.innhold.vedtakstype shouldBe Vedtakstype.ENDRING

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }

        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetVedtak shouldHaveSize 2
            val klagevedtak = it.fattetVedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val indeksreg = it.fattetVedtak.find { it.vedtaksid == vedtaksidIndeks }
            indeksreg.shouldNotBeNull()
            indeksreg.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for paragraf 35 c med orkestrering`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.ENDRING
        behandling.søknadstype = Behandlingstype.PARAGRAF_35_C
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)
        behandling.leggTilGrunnlagManuelleVedtak()
        behandling.leggTilGrunnlagEtterfølgendeVedtak()
        behandling.leggTilNotat("testtest", NotatType.PRIVAT_AVTALE, søknadsbarn)
        val privatAvtale = opprettPrivatAvtale(behandling, testdataBarn1)
        privatAvtale.skalIndeksreguleres = false
        privatAvtale.perioder.addAll(
            listOf(
                opprettPrivatAvtalePeriode(
                    privatAvtale,
                    fom = YearMonth.from(behandling.virkningstidspunkt),
                    tom = YearMonth.from(behandling.virkningstidspunkt).plusMonths(7),
                ),
                opprettPrivatAvtalePeriode(
                    privatAvtale,
                    fom = YearMonth.from(behandling.virkningstidspunkt).plusMonths(8),
                    tom = null,
                ),
            ),
        )
        behandling.privatAvtale.add(privatAvtale)
        val privaavtaleGrunnlag = behandlingTilGrunnlagMappingV2.run { behandling.tilPrivatAvtaleGrunnlag(behandling.tilPersonobjekter(), privatAvtale.rolle!!.ident!!) }

        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.ENDRING && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(
                    søknadsbarn.tilGrunnlagPerson(),
                ) + privaavtaleGrunnlag.toList(),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.INDEKSREGULERING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                grunnlagslisteDelvedtak =
                                    listOf(
                                        GrunnlagDto(
                                            type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                            innhold =
                                                POJONode(
                                                    ResultatFraVedtakGrunnlag(
                                                        vedtaksid = vedtakidsEtterfølgende,
                                                        omgjøringsvedtak = false,
                                                        beregnet = false,
                                                        vedtakstype = Vedtakstype.ENDRING,
                                                        opprettParagraf35c = false,
                                                    ),
                                                ),
                                            referanse = "",
                                        ),
                                    ),
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2025-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-08-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.ENDRING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 33
            }
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[0].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )
            val privatGrunnlag = it.grunnlagListe.first { it.type == Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG }
            val innholdPrivat = privatGrunnlag.innholdTilObjekt<PrivatAvtaleGrunnlag>()
            innholdPrivat.avtaleInngåttDato shouldBe LocalDate.parse("2024-01-01")
            innholdPrivat.skalIndeksreguleres shouldBe false
            privatGrunnlag.gjelderReferanse shouldBe behandling.bidragspliktig!!.tilGrunnlagsreferanse()
            privatGrunnlag.gjelderBarnReferanse shouldBe søknadsbarn.tilGrunnlagsreferanse()

            val privatPeriodeGrunnlag = it.grunnlagListe.filter { it.type == Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG }
            val innholdPrivatPerioder = privatPeriodeGrunnlag.innholdTilObjekt<PrivatAvtalePeriodeGrunnlag>()
            innholdPrivatPerioder.shouldHaveSize(2)
            innholdPrivatPerioder.first().beløp shouldBe BigDecimal(1000)

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[0]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }
        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INDEKSREGULERING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 14
            }
            val beregnetFraDato =
                it.stønadsendringListe
                    .first()
                    .periodeListe
                    .minOf { it.periode.fom }
                    .atDay(1)
            opprettVedtakSlot[1].unikReferanse shouldBe
                behandling.opprettUnikReferanse(
                    "Delvedtak_${it.type}" +
                        "_${beregnetFraDato.toCompactString()}",
                )

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe null
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.ENDRING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 11
            }

            opprettVedtakSlot[2].unikReferanse shouldBe
                behandling.opprettUnikReferanse("endeligvedtak")

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2025-03")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage
            orkestreringsdetaljer.innkrevesFraDato.shouldBeNull()

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                val grunnlagVirkning =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.virkningstidspunkt shouldBe søknadsbarn.virkningstidspunkt
                grunnlagVirkning.innhold.beregnTil shouldBe søknadsbarn.beregnTil
                grunnlagVirkning.innhold.beregnTilDato shouldBe behandling.finnBeregnTilDatoBehandling(søknadsbarn).toYearMonth()

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.omgjøringsvedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true
                resultatFraVedtakKlage.innhold.vedtakstype shouldBe Vedtakstype.ENDRING

                val periodeIndeks = stønadsendring.periodeListe[1]
                val resultatFraVedtakIndeks =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeIndeks.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakIndeks.shouldNotBeNull()
                resultatFraVedtakIndeks.innhold.vedtaksid shouldBe vedtaksidIndeks
                resultatFraVedtakIndeks.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakIndeks.innhold.beregnet shouldBe true
                resultatFraVedtakIndeks.innhold.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING

                val periodeEtterfølgende = stønadsendring.periodeListe[2]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
                resultatFraVedtakEtterfølgende.innhold.vedtakstype shouldBe Vedtakstype.ENDRING

                val grunnlagEtterfølgendeVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<EtterfølgendeManuelleVedtakGrunnlag>(
                            Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                            stønadsendring.grunnlagReferanseListe,
                        ).firstOrNull()
                grunnlagEtterfølgendeVedtak.shouldNotBeNull()
                grunnlagEtterfølgendeVedtak.innhold.vedtaksliste shouldHaveSize 1

                val grunnlagManuelleVedtak =
                    opprettVedtakSlot[2]
                        .grunnlagListe
                        .filtrerBasertPåEgenReferanse(grunnlagType = Grunnlagstype.MANUELLE_VEDTAK)
                        .firstOrNull()
                        ?.innholdTilObjektListe<List<ManuellVedtakGrunnlag>>()
                grunnlagManuelleVedtak.shouldNotBeNull()
                grunnlagManuelleVedtak.shouldHaveSize(1)
            }
        }

        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage uten innkreving med orkestrering`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2024-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)
        behandling.leggTilNotat("Begrunnelse virkningstidspunkt fra opprinnelig vedtak", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, false)
        behandling.leggTilNotat("Begrunnelse virkningstidspunkt", NotatType.VIRKNINGSTIDSPUNKT, søknadsbarn, true)

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        val innkrevesFraDato = LocalDate.parse("2024-07-01")
        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4
        val vedtaksidinnkreving = 5

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.INNKREVING -> vedtaksidinnkreving
                    arg.type == Vedtakstype.KLAGE && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(
                    søknadsbarn.tilGrunnlagPerson(),
                ),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(søknadsbarn.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                grunnlagslisteDelvedtak =
                                    listOf(
                                        GrunnlagDto(
                                            type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                            innhold =
                                                POJONode(
                                                    ResultatFraVedtakGrunnlag(
                                                        vedtaksid = vedtakidsEtterfølgende,
                                                        omgjøringsvedtak = false,
                                                        beregnet = false,
                                                        opprettParagraf35c = false,
                                                        vedtakstype = Vedtakstype.ENDRING,
                                                    ),
                                                ),
                                            referanse = "",
                                        ),
                                    ),
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-05-01"), LocalDate.parse("2024-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2024-05-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-05-01"), LocalDate.parse("2024-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(innkrevesFraDato, null),
                    beløp = BigDecimal("2000"),
                ),
            ),
        )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 30
            }
            val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(3)
                assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt"
                }

                assertSoftly(
                    hentNotat(
                        NotatType.VIRKNINGSTIDSPUNKT,
                        gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
                        fraOmgjortVedtak = true,
                    ),
                ) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt fra opprinnelig vedtak"
                }
            }
            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 9
            }
            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2024-03")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage
            orkestreringsdetaljer.innkrevesFraDato shouldBe innkrevesFraDato.toYearMonth()

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.omgjøringsvedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true
                resultatFraVedtakKlage.innhold.vedtakstype shouldBe Vedtakstype.KLAGE

                val periodeEtterfølgende = stønadsendring.periodeListe[1]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.omgjøringsvedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
                resultatFraVedtakEtterfølgende.innhold.vedtakstype shouldBe Vedtakstype.ENDRING
            }
        }
        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.INNKREVING
            it.grunnlagListe shouldHaveSize 8
            it.unikReferanse shouldBe behandling.opprettUnikReferanse("innkreving")
            val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(2)
                assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt"
                }

                assertSoftly(
                    hentNotat(
                        NotatType.VIRKNINGSTIDSPUNKT,
                        gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
                        fraOmgjortVedtak = true,
                    ),
                ) {
                    it shouldNotBe null
                    val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
                    innhold.innhold shouldBe "Begrunnelse virkningstidspunkt fra opprinnelig vedtak"
                }
            }
            assertSoftly(it.stønadsendringListe) { se ->
                shouldHaveSize(1)
                val stønadsendring = se.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe.shouldHaveSize(1)
                val førstePeriode = stønadsendring.periodeListe.first()
                førstePeriode.periode.fom shouldBe innkrevesFraDato.toYearMonth()
                førstePeriode.periode.til shouldBe null
                val resultatFraVedtak1 =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            førstePeriode.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtak1.shouldNotBeNull()
                resultatFraVedtak1.innhold.vedtaksid shouldBe vedtakidsOrkestrering
            }
        }
        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetVedtak shouldHaveSize 2
            val klagevedtak = it.fattetVedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val innkreving = it.fattetVedtak.find { it.vedtaksid == vedtaksidinnkreving }
            innkreving.shouldNotBeNull()
            innkreving.vedtakstype shouldBe Vedtakstype.INNKREVING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage uten innkreving med orkestrering med opphør`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.innkrevingstype = Innkrevingstype.UTEN_INNKREVING
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opphørsdato = LocalDate.parse("2999-08-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2024-01-01").atStartOfDay()),
            )

        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        initBehandlingTestdata(behandling)
        val innkrevesFraDato = LocalDate.parse("2024-07-01")

        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4
        val vedtaksidinnkreving = 5

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.INNKREVING -> vedtaksidinnkreving
                    arg.type == Vedtakstype.KLAGE && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(
                    søknadsbarn.tilGrunnlagPerson(),
                    GrunnlagDto(
                        type = no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                        innhold =
                            POJONode(
                                ResultatFraVedtakGrunnlag(
                                    vedtaksid = vedtakidsEtterfølgende,
                                    omgjøringsvedtak = false,
                                    beregnet = false,
                                    opprettParagraf35c = false,
                                    vedtakstype = Vedtakstype.ENDRING,
                                ),
                            ),
                        referanse = "",
                    ),
                ),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(søknadsbarn.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2024-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )

        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(innkrevesFraDato, null),
                    beløp = BigDecimal("2000"),
                ),
            ),
        )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.KLAGE
            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2024-03")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage
            orkestreringsdetaljer.innkrevesFraDato shouldBe innkrevesFraDato.toYearMonth()

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeOpphør = stønadsendring.periodeListe.last()
                periodeOpphør.periode.fom.shouldBe(søknadsbarn.opphørsdato!!.toYearMonth())
                periodeOpphør.periode.til.shouldBeNull()
                periodeOpphør.beløp.shouldBeNull()
                periodeOpphør.resultatkode shouldBe Resultatkode.OPPHØR.name
                val grunnlagVirkning =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            periodeOpphør.grunnlagReferanseListe,
                        ).firstOrNull()

                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.opphørsdato shouldBe søknadsbarn.opphørsdato
            }
        }
        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.INNKREVING
            it.grunnlagListe shouldHaveSize 6
            assertSoftly(it.stønadsendringListe) { se ->
                shouldHaveSize(1)
                val stønadsendring = se.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe.shouldHaveSize(2)
                val periodeFørste = stønadsendring.periodeListe.first()
                periodeFørste.periode.fom shouldBe innkrevesFraDato.toYearMonth()
                periodeFørste.periode.til shouldBe søknadsbarn.opphørsdato!!.toYearMonth()

                val periodeOpphør = stønadsendring.periodeListe.last()
                periodeOpphør.periode.fom.shouldBe(søknadsbarn.opphørsdato!!.toYearMonth())
                periodeOpphør.periode.til.shouldBeNull()
                periodeOpphør.beløp.shouldBeNull()
                periodeOpphør.resultatkode shouldBe Resultatkode.OPPHØR.name
                val grunnlagVirkning =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            periodeOpphør.grunnlagReferanseListe,
                        ).firstOrNull()

                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.opphørsdato shouldBe søknadsbarn.opphørsdato
            }
        }
        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetVedtak shouldHaveSize 2
            val klagevedtak = it.fattetVedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val innkreving = it.fattetVedtak.find { it.vedtaksid == vedtaksidinnkreving }
            innkreving.shouldNotBeNull()
            innkreving.vedtakstype shouldBe Vedtakstype.INNKREVING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage med orkestrering med opphørsdato`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2024-02-01")
        søknadsbarn.opphørsdato = LocalDate.parse("2999-08-01")
        søknadsbarn.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        initBehandlingTestdata(behandling)

        val vedtaksidKlage = 1
        val vedtaksidIndeks = 2
        val vedtakidsEtterfølgende = 3
        val vedtakidsOrkestrering = 4

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            val arg = args.last() as OpprettVedtakRequestDto
            val vedtaksid =
                when {
                    arg.type == Vedtakstype.INDEKSREGULERING -> vedtaksidIndeks
                    arg.type == Vedtakstype.KLAGE && arg.stønadsendringListe.any { it.beslutning == Beslutningstype.DELVEDTAK } -> vedtaksidKlage
                    else -> vedtakidsOrkestrering
                }
            OpprettVedtakResponseDto(
                vedtaksid,
                emptyList(),
            )
        }
        every { bidragsberegningOrkestrator.utførBidragsberegningV2(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                listOf(
                    søknadsbarn.tilGrunnlagPerson(),
                    GrunnlagDto(
                        type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                        innhold =
                            POJONode(
                                ResultatFraVedtakGrunnlag(
                                    vedtaksid = vedtakidsEtterfølgende,
                                    omgjøringsvedtak = false,
                                    beregnet = false,
                                    vedtakstype = Vedtakstype.ENDRING,
                                    opprettParagraf35c = false,
                                ),
                            ),
                        referanse = "",
                    ),
                ) + byggGrunnlagForBeregning(behandling, søknadsbarn),
                listOf(
                    BidragsberegningResultatBarnV2(
                        søknadsbarn.tilGrunnlagsreferanse(),
                        listOf(
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.INDEKSREGULERING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-06-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.ENDRING,
                                omgjøringsvedtak = false,
                                delvedtak = true,
                                beregnet = false,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), null),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                            ResultatVedtakV2(
                                vedtakstype = Vedtakstype.KLAGE,
                                omgjøringsvedtak = false,
                                beregnet = true,
                                periodeListe =
                                    listOf(
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2025-06-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-06-01"), LocalDate.parse("2025-07-01")),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                        ResultatPeriode(
                                            periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), søknadsbarn.opphørsdato),
                                            resultat = ResultatBeregning(BigDecimal.ZERO),
                                            grunnlagsreferanseListe = emptyList(),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                ),
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 29
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }
        assertSoftly(opprettVedtakSlot[1]) {
            it.type shouldBe Vedtakstype.INDEKSREGULERING
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 14
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()

            assertSoftly(it.stønadsendringListe) {
                shouldHaveSize(1)
                val stønadsendring = first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe null
                stønadsendring.beslutning shouldBe Beslutningstype.DELVEDTAK
            }
        }

        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 9
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2025-02")
            orkestreringsdetaljer.omgjøringsvedtakId shouldBe vedtaksidKlage

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(4)

                val periodeOpphør = stønadsendring.periodeListe[3]
                periodeOpphør.periode.fom.shouldBe(søknadsbarn.opphørsdato!!.toYearMonth())
                periodeOpphør.periode.til.shouldBeNull()
                periodeOpphør.beløp.shouldBeNull()
                periodeOpphør.resultatkode shouldBe Resultatkode.OPPHØR.name
                val grunnlagVirkning =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<VirkningstidspunktGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            periodeOpphør.grunnlagReferanseListe,
                        ).firstOrNull()

                grunnlagVirkning.shouldNotBeNull()
                grunnlagVirkning.innhold.opphørsdato shouldBe søknadsbarn.opphørsdato
            }
        }

        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetVedtak shouldHaveSize 2
            val klagevedtak = it.fattetVedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val indeksreg = it.fattetVedtak.find { it.vedtaksid == vedtaksidIndeks }
            indeksreg.shouldNotBeNull()
            indeksreg.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    private fun initBehandlingTestdata(behandling: Behandling) {
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Begrunnelse samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.finnAlleRelaterteBehandlinger(any()) } returns listOf(behandling)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
    }
}

private fun OpprettVedtakRequestDto.validerNotater() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val bpGunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
        shouldHaveSize(16)
        assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse virkningstidspunkt"
        }

        assertSoftly(hentNotat(NotatType.VIRKNINGSTIDSPUNKT, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse virkningstidspunkt fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.PRIVAT_AVTALE, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse privat avtale"
        }

        assertSoftly(hentNotat(NotatType.PRIVAT_AVTALE, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse privat avtale fra opprinnelig vedtak"
        }
        assertSoftly(hentNotat(NotatType.BOFORHOLD)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse boforhold"
        }
        assertSoftly(hentNotat(NotatType.BOFORHOLD, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse boforhold fra opprinnelig vedtak"
        }
        assertSoftly(hentNotat(NotatType.SAMVÆR, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse samvær"
        }

        assertSoftly(hentNotat(NotatType.SAMVÆR, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse samvær fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse underhold"
        }
        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderBarnReferanse = søknadsbarnGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse underhold fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse underhold BM"
        }
        assertSoftly(hentNotat(NotatType.UNDERHOLDSKOSTNAD, gjelderReferanse = bmGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse underhold BM fra opprinnelig vedtak"
        }

        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse inntekt BM"
        }
        assertSoftly(hentNotat(NotatType.INNTEKT, gjelderReferanse = bmGrunnlag.referanse, fraOmgjortVedtak = true)) {
            it shouldNotBe null
            val innhold = it!!.innholdTilObjekt<NotatGrunnlag>()
            innhold.innhold shouldBe "Begrunnelse inntekt BM fra opprinnelig vedtak"
        }
    }
}
