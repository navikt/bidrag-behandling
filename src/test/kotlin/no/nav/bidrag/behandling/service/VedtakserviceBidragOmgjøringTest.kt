package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import no.nav.bidrag.behandling.config.UnleashFeatures
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.json.FatteVedtakDetaljerFraOmgjortVedtakForRevurderingsbarn
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingSøknadBarn
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.dto.v2.vedtak.FatteVedtakRequestDto
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.hentBehandlingDetaljer
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.utils.enableUnleashFeature
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.testdata.lagVedtaksdata
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagManuelleVedtak
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.beregn.barnebidrag.service.orkestrering.BidragsberegningOrkestrator
import no.nav.bidrag.beregn.barnebidrag.utils.tilDto
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorResponseV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningResultatBarnV2
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtakV2
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningAndelAvBidragsevne
import no.nav.bidrag.transport.behandling.felles.grunnlag.FatteVedtakRevurderingsbarn
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(SpringExtension::class)
class VedtakserviceBidragOmgjøringTest : CommonVedtakTilBehandlingTest() {
    @BeforeEach
    fun initMocksOmgjøring() {
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
                bbmConsumer,
                bestillAsyncJobService = bestillAsyncJobService,
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
                behandlingRepository = behandlingRepository,
                forholdsmessigFordelingService = forholdsmessigFordelingService,
            )
    }

    @Test
    fun `Skal bare fatte vedtak for klagevedtak hvis klagevedtak er eneste vedtak`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandling()
        val opprettVedtakSlot = mockVedtakFatteVedtak()

        mockBidragsberegning(behandling, klagevedtakEnesteVedtak = true)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        opprettVedtakSlot shouldHaveSize 1
        verify(exactly = 1) { vedtakConsumer.fatteVedtak(any()) }
        assertSoftly(opprettVedtakSlot) {
            first().type shouldBe Vedtakstype.KLAGE
            first().stønadsendringListe shouldHaveSize 1
        }
    }

    @Test
    fun `opprettVedtakRequestDelvedtakV2 - to barn - omgjøringsvedtak for begge barn slås sammen til ett delvedtak`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandling(toBarn = true)
        val opprettVedtakSlot = mockVedtakFatteVedtak()

        mockBidragsberegning(behandling)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        opprettVedtakSlot shouldHaveSize 2
        verify(exactly = 2) { vedtakConsumer.fatteVedtak(any()) }
        assertSoftly(opprettVedtakSlot) {
            first().type shouldBe Vedtakstype.KLAGE
            first().stønadsendringListe shouldHaveSize 2
            get(1).type shouldBe Vedtakstype.KLAGE
            get(1).stønadsendringListe shouldHaveSize 2
        }
    }

    @Test
    fun `fatter ett endelig vedtak når behandling ikke er i forholdsmessig fordeling`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandling(toBarn = true)
        val opprettVedtakSlot = mockVedtakFatteVedtak()

        mockBidragsberegning(behandling)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        opprettVedtakSlot shouldHaveSize 2
        verify(exactly = 2) { vedtakConsumer.fatteVedtak(any()) }
        opprettVedtakSlot[1].stønadsendringListe shouldHaveSize 2
    }

    @Test
    fun `splitter endelig vedtak per søknadsid nar FF og BP har full evne i alle perioder`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandling(toBarn = true, forholdsmessigFordeling = true)
        behandling.søknadsbarn.first().forholdsmessigFordeling = opprettForholdsmessigFordelingRolle(behandling, 101L)
        behandling.søknadsbarn[1].forholdsmessigFordeling = opprettForholdsmessigFordelingRolle(behandling, 102L)
        val opprettVedtakSlot = mockVedtakFatteVedtak()

        mockBidragsberegning(behandling)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        opprettVedtakSlot shouldHaveSize 3
        verify(exactly = 3) { vedtakConsumer.fatteVedtak(any()) }
        assertSoftly(opprettVedtakSlot) {
            first().stønadsendringListe shouldHaveSize 2
            get(1).stønadsendringListe shouldHaveSize 1
            get(2).stønadsendringListe shouldHaveSize 1
        }
    }

    @Test
    fun `fatter ett endelig vedtak når FF men BP ikke har full evne i alle perioder`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandling(toBarn = true, forholdsmessigFordeling = true)
        behandling.søknadsbarn.first().forholdsmessigFordeling = opprettForholdsmessigFordelingRolle(behandling, 101L)
        behandling.søknadsbarn[1].forholdsmessigFordeling = opprettForholdsmessigFordelingRolle(behandling, 102L)
        val opprettVedtakSlot = mockVedtakFatteVedtak()

        mockBidragsberegning(behandling, bpHarIkkeFullEvneIAllePerioder = true)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        opprettVedtakSlot shouldHaveSize 2
        verify(exactly = 2) { vedtakConsumer.fatteVedtak(any()) }
        opprettVedtakSlot[1].stønadsendringListe shouldHaveSize 2
    }

    private fun opprettOmgjøringsbehandling(
        toBarn: Boolean = false,
        forholdsmessigFordeling: Boolean = false,
    ): Behandling {
        val behandling =
            opprettGyldigBehandlingForBeregningOgVedtak(
                generateId = true,
                typeBehandling = TypeBehandling.BIDRAG,
                andreBarn = if (toBarn) listOf(testdataBarn2) else emptyList(),
            )

        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                klageMottattdato = LocalDate.parse("2025-01-10"),
                omgjørVedtakId = 2,
                opprinneligVedtakId = 3,
                opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01"),
                omgjortVedtakstidspunktListe = mutableSetOf(LocalDate.parse("2025-01-01").atStartOfDay()),
            )
        behandling.forholdsmessigFordeling = if (forholdsmessigFordeling) ForholdsmessigFordeling(erHovedbehandling = true) else null

        behandling.søknadsbarn.forEach {
            it.virkningstidspunkt = LocalDate.parse("2025-02-01")
            it.opprinneligVirkningstidspunkt = LocalDate.parse("2025-01-01")
            it.beregnTil = BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
            it.forholdsmessigFordeling =
                ForholdsmessigFordelingRolle(
                    tilhørerSak = behandling.saksnummer,
                    behandlerenhet = "",
                    delAvOpprinneligBehandling = true,
                    erRevurdering = false,
                    bidragsmottaker = behandling.bidragsmottaker!!.ident,
                    søknader =
                        mutableSetOf(
                            ForholdsmessigFordelingSøknadBarn(
                                søknadsid = 123,
                                mottattDato = LocalDate.parse("2025-01-01"),
                                søktAvType = SøktAvType.BIDRAGSMOTTAKER,
                                behandlingstype = Behandlingstype.ENDRING,
                                behandlingstema = Behandlingstema.BIDRAG,
                            ),
                        ),
                )
        }
        behandling.virkningstidspunkt = behandling.søknadsbarn.first().virkningstidspunkt

        initBehandlingTestdata(behandling)

        behandling.leggTilGrunnlagManuelleVedtak(behandling.søknadsbarn.first())
        if (toBarn) {
            behandling.søknadsbarn[1].grunnlagFraVedtak = 1
            behandling.leggTilGrunnlagManuelleVedtak(behandling.søknadsbarn[1])
        }
        return behandling
    }

    private fun mockVedtakFatteVedtak(): MutableList<OpprettVedtakRequestDto> {
        val opprettVedtakSlot = mutableListOf<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } answers {
            OpprettVedtakResponseDto(opprettVedtakSlot.size, emptyList())
        }
        return opprettVedtakSlot
    }

    private fun mockBidragsberegning(
        behandling: Behandling,
        bpHarIkkeFullEvneIAllePerioder: Boolean = false,
        revurderingsbarnHarTommePerioder: Boolean = false,
        klagevedtakEnesteVedtak: Boolean = false,
    ) {
        val vedtakidsEtterfølgende = 99
        val grunnlagListe =
            behandling.søknadsbarn.map { it.tilGrunnlagPerson() } +
                if (bpHarIkkeFullEvneIAllePerioder) {
                    behandling.søknadsbarn.mapIndexed { index, søknadsbarn ->
                        GrunnlagDto(
                            referanse = "delberegning-andel-barn${index + 1}",
                            type = Grunnlagstype.DELBEREGNING_ANDEL_AV_BIDRAGSEVNE,
                            innhold =
                                POJONode(
                                    DelberegningAndelAvBidragsevne(
                                        periode = ÅrMånedsperiode(behandling.virkningstidspunkt!!, null),
                                        sumBidragTilFordelingJustertForPrioriterteBidrag = BigDecimal.ZERO,
                                        evneJustertForPrioriterteBidrag = BigDecimal.ZERO,
                                        andelAvSumBidragTilFordelingFaktor = BigDecimal.ZERO,
                                        andelAvEvneBeløp = BigDecimal.ZERO,
                                        bidragEtterFordeling = BigDecimal.ZERO,
                                        bruttoBidragJustertForEvneOg25Prosent = BigDecimal.ZERO,
                                        harBPFullEvne = false,
                                    ),
                                ),
                            gjelderBarnReferanse = søknadsbarn.tilGrunnlagsreferanse(),
                        )
                    }
                } else {
                    emptyList()
                }

        val referanseReferertVedtak = "referert_vedtak_revurderingsbarn"
        every { bidragsberegningOrkestrator.utførBidragsberegningV3(any()) } returns
            BidragsberegningOrkestratorResponseV2(
                grunnlagListe,
                behandling.søknadsbarn.map { søknadsbarn ->
                    val erRevurderingsbarn = søknadsbarn.erRevurderingsbarn
                    if (erRevurderingsbarn) {
                        BidragsberegningResultatBarnV2(
                            søknadsbarn.tilGrunnlagsreferanse(),
                            avvistRevurderingsbarn = revurderingsbarnHarTommePerioder,
                            resultatVedtakListe =
                                listOf(
                                    ResultatVedtakV2(
                                        vedtakstype = Vedtakstype.KLAGE,
                                        omgjøringsvedtak = !revurderingsbarnHarTommePerioder,
                                        beregnet = !revurderingsbarnHarTommePerioder,
                                        delvedtak = true,
                                        periodeListe =
                                            if (revurderingsbarnHarTommePerioder) {
                                                emptyList()
                                            } else {
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
                                                )
                                            },
                                    ),
                                    ResultatVedtakV2(
                                        vedtakstype = Vedtakstype.KLAGE,
                                        omgjøringsvedtak = false,
                                        beregnet = false,
                                        delvedtak = false,
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
                                                    referanse = referanseReferertVedtak,
                                                ),
                                            ),
                                        periodeListe =
                                            if (revurderingsbarnHarTommePerioder) {
                                                listOf(
                                                    ResultatPeriode(
                                                        periode =
                                                            ÅrMånedsperiode(
                                                                behandling.virkningstidspunkt!!,
                                                                behandling.finnBeregnTilDatoBehandling(søknadsbarn),
                                                            ),
                                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                                        grunnlagsreferanseListe = listOf(referanseReferertVedtak),
                                                    ),
                                                )
                                            } else {
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
                                                )
                                            },
                                    ),
                                ),
                        )
                    } else {
                        BidragsberegningResultatBarnV2(
                            søknadsbarn.tilGrunnlagsreferanse(),
                            avvistRevurderingsbarn = revurderingsbarnHarTommePerioder,
                            resultatVedtakListe =
                                if (klagevedtakEnesteVedtak) {
                                    listOf(
                                        ResultatVedtakV2(
                                            vedtakstype = Vedtakstype.KLAGE,
                                            omgjøringsvedtak = true,
                                            beregnet = true,
                                            delvedtak = true,
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
                                            beregnet = false,
                                            delvedtak = false,
                                            grunnlagslisteDelvedtak = emptyList(),
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
                                    )
                                } else {
                                    listOf(
                                        ResultatVedtakV2(
                                            vedtakstype = Vedtakstype.KLAGE,
                                            omgjøringsvedtak = false,
                                            beregnet = false,
                                            delvedtak = true,
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
                                                        referanse = referanseReferertVedtak,
                                                    ),
                                                ),
                                            periodeListe =
                                                listOf(
                                                    ResultatPeriode(
                                                        periode =
                                                            ÅrMånedsperiode(
                                                                behandling.virkningstidspunkt!!,
                                                                behandling.virkningstidspunkt!!.plusMonths(1),
                                                            ),
                                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                                        grunnlagsreferanseListe = listOf(referanseReferertVedtak),
                                                    ),
                                                ),
                                        ),
                                        ResultatVedtakV2(
                                            vedtakstype = Vedtakstype.KLAGE,
                                            omgjøringsvedtak = true,
                                            beregnet = true,
                                            delvedtak = true,
                                            periodeListe =
                                                listOf(
                                                    ResultatPeriode(
                                                        periode =
                                                            ÅrMånedsperiode(
                                                                behandling.virkningstidspunkt!!.plusMonths(1),
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
                                            beregnet = false,
                                            delvedtak = false,
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
                                                        referanse = referanseReferertVedtak,
                                                    ),
                                                ),
                                            periodeListe =
                                                listOf(
                                                    ResultatPeriode(
                                                        periode =
                                                            ÅrMånedsperiode(
                                                                behandling.virkningstidspunkt!!,
                                                                behandling.virkningstidspunkt!!.plusMonths(1),
                                                            ),
                                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                                        grunnlagsreferanseListe = listOf(referanseReferertVedtak),
                                                    ),
                                                    ResultatPeriode(
                                                        periode =
                                                            ÅrMånedsperiode(
                                                                behandling.virkningstidspunkt!!.plusMonths(1),
                                                                behandling.finnBeregnTilDatoBehandling(søknadsbarn),
                                                            ),
                                                        resultat = ResultatBeregning(BigDecimal.ZERO),
                                                        grunnlagsreferanseListe = emptyList(),
                                                    ),
                                                ),
                                        ),
                                    )
                                },
                        )
                    }
                },
            )
        every { vedtakServiceBeregning.finnSisteVedtaksid(any()) } returns 1
    }

    private fun opprettForholdsmessigFordelingRolle(
        behandling: Behandling,
        søknadsid: Long,
        erRevurdering: Boolean = false,
    ) = ForholdsmessigFordelingRolle(
        tilhørerSak = behandling.saksnummer,
        behandlerenhet = behandling.behandlerEnhet,
        delAvOpprinneligBehandling = true,
        erRevurdering = erRevurdering,
        bidragsmottaker = behandling.bidragsmottaker?.ident,
        søknader =
            mutableSetOf(
                ForholdsmessigFordelingSøknadBarn(
                    mottattDato = LocalDate.parse("2025-01-01"),
                    søktAvType = SøktAvType.BIDRAGSMOTTAKER,
                    søknadsid = søknadsid,
                    status = null,
                    behandlingstype = null,
                    behandlingstema = null,
                ),
            ),
    )

    private fun initBehandlingTestdata(behandling: Behandling) {
        behandling.søknadsbarn.forEachIndexed { index, søknadsbarn ->
            val barn = if (index == 0) testdataBarn1 else testdataBarn2
            behandling.leggTilSamvær(
                ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)),
                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1,
                barn = barn,
                medId = true,
            )
            behandling.leggTilSamvær(
                ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null),
                barn = barn,
                medId = true,
            )
            behandling.leggTilNotat(
                "Begrunnelse samvær",
                NotatType.SAMVÆR,
                søknadsbarn,
            )
        }
        val originalVedtak = lagVedtaksdata("fattetvedtak/bidrag-innvilget")

        behandling.søknadsbarn.first().grunnlagFraVedtak = 1
        every { vedtakConsumer.hentVedtak(any()) } returns originalVedtak
        every { behandlingRepository.finnAlleRelaterteBehandlinger(any()) } returns listOf(behandling)
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
    }

    private fun opprettOmgjøringsbehandlingMedRevurderingsbarn(
        fatteVedtakDetaljerRevurderingsbarn: FatteVedtakDetaljerFraOmgjortVedtakForRevurderingsbarn? = null,
    ): Behandling {
        val behandling = opprettOmgjøringsbehandling(toBarn = true)
        behandling.søknadsbarn[1].grunnlagFraVedtak = 1
        behandling.leggTilGrunnlagManuelleVedtak(behandling.søknadsbarn[1])
        behandling.søknadsbarn[1].forholdsmessigFordeling =
            opprettForholdsmessigFordelingRolle(behandling, 102L, erRevurdering = true)
        behandling.omgjøringsdetaljer =
            behandling.omgjøringsdetaljer!!.copy(
                fatteVedtakDetaljerRevurderingsbarn = fatteVedtakDetaljerRevurderingsbarn,
            )
        behandling.forholdsmessigFordeling = ForholdsmessigFordeling(erHovedbehandling = true)
        return behandling
    }

    @Test
    fun `Vedtak stønadsendring beslutningstype for revurderingsbarn blir ENDRING når bleFattetVedtakForRevurderingsbarn er true og det er full evne i alle perioder`() {
        stubPersonConsumer()
        val behandling =
            opprettOmgjøringsbehandlingMedRevurderingsbarn(
                fatteVedtakDetaljerRevurderingsbarn =
                    FatteVedtakDetaljerFraOmgjortVedtakForRevurderingsbarn(
                        bleFattetVedtakForRevurderingsbarn = true,
                        fatteVedtakRevurderingsbarn = null,
                    ),
            )
        val opprettVedtakSlot = mockVedtakFatteVedtak()
        mockBidragsberegning(behandling, revurderingsbarnHarTommePerioder = true)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        val endeligVedtak = opprettVedtakSlot.last()
        val revurderingsbarnStønadsendring =
            endeligVedtak.stønadsendringListe.find { it.kravhaver.verdi == testdataBarn2.ident }
        revurderingsbarnStønadsendring?.beslutning shouldBe Beslutningstype.ENDRING
    }

    @Test
    fun `Vedtak stønadsendring for revurderingsbarn blir AVVIST når bleFattetVedtakForRevurderingsbarn er false og det er full evne i alle perioder`() {
        stubPersonConsumer()
        val behandling =
            opprettOmgjøringsbehandlingMedRevurderingsbarn(
                fatteVedtakDetaljerRevurderingsbarn =
                    FatteVedtakDetaljerFraOmgjortVedtakForRevurderingsbarn(
                        bleFattetVedtakForRevurderingsbarn = false,
                        fatteVedtakRevurderingsbarn = null,
                    ),
            )
        val opprettVedtakSlot = mockVedtakFatteVedtak()
        mockBidragsberegning(behandling, revurderingsbarnHarTommePerioder = true)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        val endeligVedtak = opprettVedtakSlot.last()
        val revurderingsbarnStønadsendring =
            endeligVedtak.stønadsendringListe.find { it.kravhaver.verdi == testdataBarn2.ident }
        revurderingsbarnStønadsendring?.beslutning shouldBe Beslutningstype.AVVIST
    }

    @Test
    fun `Vedtak for revurderingsbarn blir fattet med beslutningstype ENDRING når bleFattetVedtakForRevurderingsbarn fra omgjort vedtak ikke er satt`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandlingMedRevurderingsbarn(fatteVedtakDetaljerRevurderingsbarn = null)
        val opprettVedtakSlot = mockVedtakFatteVedtak()
        mockBidragsberegning(behandling, revurderingsbarnHarTommePerioder = true)

        vedtakService.fatteVedtak(behandling.id!!, FatteVedtakRequestDto())

        val endeligVedtak = opprettVedtakSlot.last()
        val revurderingsbarnStønadsendring =
            endeligVedtak.stønadsendringListe.find { it.kravhaver.verdi == testdataBarn2.ident }
        revurderingsbarnStønadsendring?.beslutning shouldBe Beslutningstype.AVVIST
    }

    @Test
    fun `Vedtak for revurderingsbarn blir fattet med beslutningstype AVVIST når saksbehandler manuelt overstyrer til ikke å fatte vedtak`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandlingMedRevurderingsbarn(fatteVedtakDetaljerRevurderingsbarn = null)
        val opprettVedtakSlot = mockVedtakFatteVedtak()
        mockBidragsberegning(behandling)

        vedtakService.fatteVedtak(
            behandling.id!!,
            FatteVedtakRequestDto(
                fatteVedtakRevurderingsbarn = FatteVedtakRevurderingsbarn(foreslåttFatteVedtak = true, manueltOverstyrtForslagBegrunnelse = "Dette er test"),
            ),
        )

        val endeligVedtak = opprettVedtakSlot.last()
        val revurderingsbarnStønadsendring =
            endeligVedtak.stønadsendringListe.find { it.kravhaver.verdi == testdataBarn2.ident }
        revurderingsbarnStønadsendring?.beslutning shouldBe Beslutningstype.AVVIST
        val behandlingsdetaljer = endeligVedtak.grunnlagListe.map { it.tilDto() }.hentBehandlingDetaljer()
        behandlingsdetaljer?.fatteVedtakRevurderingsbarn?.manueltOverstyrtForslagBegrunnelse shouldBe "Dette er test"
    }

    @Test
    fun `Vedtak for revurderingsbarn blir fattet med beslutningstype ENDRING når saksbehandler manuelt overstyrer til å fatte vedtak`() {
        stubPersonConsumer()
        val behandling = opprettOmgjøringsbehandlingMedRevurderingsbarn(fatteVedtakDetaljerRevurderingsbarn = null)
        val opprettVedtakSlot = mockVedtakFatteVedtak()
        mockBidragsberegning(behandling)

        vedtakService.fatteVedtak(
            behandling.id!!,
            FatteVedtakRequestDto(
                fatteVedtakRevurderingsbarn = FatteVedtakRevurderingsbarn(foreslåttFatteVedtak = false, manueltOverstyrtForslagBegrunnelse = "Dette er test"),
            ),
        )

        val endeligVedtak = opprettVedtakSlot.last()
        val revurderingsbarnStønadsendring =
            endeligVedtak.stønadsendringListe.find { it.kravhaver.verdi == testdataBarn2.ident }
        revurderingsbarnStønadsendring?.beslutning shouldBe Beslutningstype.ENDRING
        val behandlingsdetaljer = endeligVedtak.grunnlagListe.map { it.tilDto() }.hentBehandlingDetaljer()
        behandlingsdetaljer?.fatteVedtakRevurderingsbarn?.manueltOverstyrtForslagBegrunnelse shouldBe "Dette er test"
    }
}
