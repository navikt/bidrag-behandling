package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import no.nav.bidrag.behandling.database.datamodell.json.Klagedetaljer
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.dto.v2.vedtak.OppdaterParagraf35cDetaljerDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.lagVedtaksdata
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagManuelleVedtak
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.beregn.barnebidrag.utils.tilDto
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BeregnetBarnebidragResultat
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorResponse
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtak
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import no.nav.bidrag.transport.behandling.vedtak.response.finnOrkestreringDetaljer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional

@ExtendWith(SpringExtension::class)
class VedtakserviceBidragKlageTest : CommonVedtakTilBehandlingTest() {
    @BeforeEach
    fun initMocksKlage() {
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
                vedtakLocalConsumer,
                validerBeregning,
                vedtakTilBehandlingMapping,
                behandlingTilVedtakMapping,
                validerBehandlingService,
                forsendelseService,
            )
    }

    @Test
    fun `Skal fatte vedtak for klage`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.virkningstidspunkt = søknadsbarn.virkningstidspunkt
        behandling.klagedetaljer =
            Klagedetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                påklagetVedtak = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )
        every { bidragsberegningOrkestrator.utførBidragsberegning(any()) } returns
            BidragsberegningOrkestratorResponse(
                listOf(
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = true,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = false,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
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
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        val opprettVedtakRequest = opprettVedtakSlot.first()

        opprettVedtakSlot shouldHaveSize 1
        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 14
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe) {
            shouldHaveSize(1)
            val stønadsendring = opprettVedtakRequest.stønadsendringListe.first()
            stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            stønadsendring.omgjørVedtakId shouldBe 2
            stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
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
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.klagedetaljer =
            Klagedetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                påklagetVedtak = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilGrunnlagManuelleVedtak(
            behandling.søknadsbarn.first(),
        )
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1

        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

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
        every { bidragsberegningOrkestrator.utførBidragsberegning(any()) } returns
            BidragsberegningOrkestratorResponse(
                listOf(
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = true,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = false,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
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
                it.grunnlagListe shouldHaveSize 14
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
                it.grunnlagListe shouldHaveSize 3
            }

            assertSoftly(it.stønadsendringListe) { se ->
                se.shouldHaveSize(1)
                val stønadsendring = it.stønadsendringListe.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe shouldHaveSize 1
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
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal fatte vedtak for klage med orkestrering`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        behandling.vedtakstype = Vedtakstype.KLAGE
        søknadsbarn.virkningstidspunkt = LocalDate.parse("2025-02-01")
        søknadsbarn.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
        behandling.klagedetaljer =
            Klagedetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                påklagetVedtak = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

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
        every { bidragsberegningOrkestrator.utførBidragsberegning(any()) } returns
            BidragsberegningOrkestratorResponse(
                listOf(
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = true,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.INDEKSREGULERING,
                        klagevedtak = false,
                        delvedtak = true,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2025-07-01"), null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.ENDRING,
                        klagevedtak = false,
                        delvedtak = true,
                        beregnet = false,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2025-08-01"), null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                                listOf(
                                    GrunnlagDto(
                                        type = no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                        innhold =
                                            POJONode(
                                                ResultatFraVedtakGrunnlag(
                                                    vedtaksid = vedtakidsEtterfølgende,
                                                    klagevedtak = false,
                                                    beregnet = false,
                                                    opprettParagraf35c = false,
                                                ),
                                            ),
                                        referanse = "",
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = false,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
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
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1

        vedtakService.oppdaterParagrafP35c(behandling.id!!, OppdaterParagraf35cDetaljerDto(søknadsbarn.ident!!, vedtakidsEtterfølgende, true))
        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto(innkrevingUtsattAntallDager = null))

        opprettVedtakSlot shouldHaveSize 3
        assertSoftly(opprettVedtakSlot.first()) {
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 12
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
                it.grunnlagListe shouldHaveSize 10
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
                it.grunnlagListe shouldHaveSize 6
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2025-03")
            orkestreringsdetaljer.klagevedtakId shouldBe vedtaksidKlage

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(3)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.klagevedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true

                val periodeIndeks = stønadsendring.periodeListe[1]
                val resultatFraVedtakIndeks =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeIndeks.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakIndeks.shouldNotBeNull()
                resultatFraVedtakIndeks.innhold.vedtaksid shouldBe vedtaksidIndeks
                resultatFraVedtakIndeks.innhold.klagevedtak shouldBe false
                resultatFraVedtakIndeks.innhold.beregnet shouldBe true

                val periodeEtterfølgende = stønadsendring.periodeListe[2]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.klagevedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
            }
        }

        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetDelvedtak shouldHaveSize 2
            val klagevedtak = it.fattetDelvedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val indeksreg = it.fattetDelvedtak.find { it.vedtaksid == vedtaksidIndeks }
            indeksreg.shouldNotBeNull()
            indeksreg.vedtakstype shouldBe Vedtakstype.INDEKSREGULERING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
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
        behandling.klagedetaljer =
            Klagedetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                påklagetVedtak = 2,
                opprinneligVirkningstidspunkt = LocalDate.parse("2024-01-01"),
                opprinneligVedtakstidspunkt = mutableSetOf(LocalDate.parse("2024-01-01").atStartOfDay()),
            )
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
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
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

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
        every { bidragsberegningOrkestrator.utførBidragsberegning(any()) } returns
            BidragsberegningOrkestratorResponse(
                listOf(
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = true,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(søknadsbarn.virkningstidspunkt!!, null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.ENDRING,
                        klagevedtak = false,
                        delvedtak = true,
                        beregnet = false,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                        grunnlagsreferanseListe = emptyList(),
                                    ),
                                ),
                                listOf(
                                    GrunnlagDto(
                                        type = no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                        innhold =
                                            POJONode(
                                                ResultatFraVedtakGrunnlag(
                                                    vedtaksid = vedtakidsEtterfølgende,
                                                    klagevedtak = false,
                                                    beregnet = false,
                                                    opprettParagraf35c = false,
                                                ),
                                            ),
                                        referanse = "",
                                    ),
                                ),
                            ),
                    ),
                    ResultatVedtak(
                        vedtakstype = Vedtakstype.KLAGE,
                        klagevedtak = false,
                        beregnet = true,
                        resultat =
                            BeregnetBarnebidragResultat(
                                listOf(
                                    ResultatPeriode(
                                        periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, LocalDate.parse("2024-07-01")),
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
            )

        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
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
                it.grunnlagListe shouldHaveSize 14
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
            it.type shouldBe Vedtakstype.KLAGE
            withClue("Grunnlagliste skal inneholde ${it.grunnlagListe.size} grunnlag") {
                it.grunnlagListe shouldHaveSize 5
            }
//            request.unikReferanse shouldBe behandling.opprettUnikReferanse()

            val orkestreringsdetaljer = it.grunnlagListe.map { it.tilDto() }.finnOrkestreringDetaljer(it.stønadsendringListe.first().grunnlagReferanseListe)
            orkestreringsdetaljer.shouldNotBeNull()
            orkestreringsdetaljer.beregnTilDato shouldBe YearMonth.parse("2024-03")
            orkestreringsdetaljer.klagevedtakId shouldBe vedtaksidKlage

            assertSoftly(it.stønadsendringListe) { sh ->
                sh.shouldHaveSize(1)
                val stønadsendring = sh.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.UTEN_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING

                val perioder = stønadsendring.periodeListe
                perioder.shouldHaveSize(2)

                val periodeKlagevedtak = stønadsendring.periodeListe[0]
                val resultatFraVedtakKlage =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeKlagevedtak.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakKlage.shouldNotBeNull()
                resultatFraVedtakKlage.innhold.vedtaksid shouldBe vedtaksidKlage
                resultatFraVedtakKlage.innhold.klagevedtak shouldBe true
                resultatFraVedtakKlage.innhold.beregnet shouldBe true

                val periodeEtterfølgende = stønadsendring.periodeListe[1]
                val resultatFraVedtakEtterfølgende =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            periodeEtterfølgende.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtakEtterfølgende.shouldNotBeNull()
                resultatFraVedtakEtterfølgende.innhold.vedtaksid shouldBe vedtakidsEtterfølgende
                resultatFraVedtakEtterfølgende.innhold.klagevedtak shouldBe false
                resultatFraVedtakEtterfølgende.innhold.beregnet shouldBe false
                resultatFraVedtakEtterfølgende.innhold.opprettParagraf35c shouldBe true
            }
        }
        assertSoftly(opprettVedtakSlot[2]) {
            it.type shouldBe Vedtakstype.INNKREVING
            it.grunnlagListe shouldHaveSize 3
            assertSoftly(it.stønadsendringListe) { se ->
                shouldHaveSize(1)
                val stønadsendring = se.first()
                stønadsendring.innkreving shouldBe Innkrevingstype.MED_INNKREVING
                stønadsendring.omgjørVedtakId shouldBe 2
                stønadsendring.beslutning shouldBe Beslutningstype.ENDRING
                stønadsendring.periodeListe.shouldHaveSize(2)
                val førstePeriode = stønadsendring.periodeListe.first()
                val resultatFraVedtak1 =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            førstePeriode.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtak1.shouldNotBeNull()
                resultatFraVedtak1.innhold.vedtaksid shouldBe vedtakidsOrkestrering
                val andrePeriode = stønadsendring.periodeListe[1]
                val resultatFraVedtak2 =
                    it.grunnlagListe
                        .finnOgKonverterGrunnlagSomErReferertFraGrunnlagsreferanseListe<ResultatFraVedtakGrunnlag>(
                            no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype.RESULTAT_FRA_VEDTAK,
                            andrePeriode.grunnlagReferanseListe,
                        ).firstOrNull()
                resultatFraVedtak2.shouldNotBeNull()
                resultatFraVedtak2.innhold.vedtaksid shouldBe vedtakidsOrkestrering
            }
        }
        assertSoftly(behandling.vedtakDetaljer!!) {
            behandling.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtaksid shouldBe vedtakidsOrkestrering
            it.vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
            it.vedtakFattetAvEnhet shouldBe "4806"
            it.fattetDelvedtak shouldHaveSize 2
            val klagevedtak = it.fattetDelvedtak.find { it.vedtaksid == vedtaksidKlage }
            klagevedtak.shouldNotBeNull()
            klagevedtak.vedtakstype shouldBe Vedtakstype.KLAGE

            val innkreving = it.fattetDelvedtak.find { it.vedtaksid == vedtaksidinnkreving }
            innkreving.shouldNotBeNull()
            innkreving.vedtakstype shouldBe Vedtakstype.INNKREVING
        }
        verify(exactly = 3) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 0) { notatOpplysningerService.opprettNotat(any()) }
    }
}
