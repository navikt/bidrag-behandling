package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.hentGrunnlagstype
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentGrunnlagstyperForReferanser
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.shouldContainPerson
import no.nav.bidrag.behandling.utils.søknad
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandlingMedReelMottaker
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.behandling.utils.virkningsdato
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.AldersgruppeForskudd
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeregnetInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningBarnIHusstand
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningForskudd
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåFremmedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubHentPersonNyIdent
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@ExtendWith(SpringExtension::class)
class VedtakserviceForskuddTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService
    lateinit var personConsumer: BidragPersonConsumer

    val unleash = FakeUnleash()

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        unleash.enableAll()
        personConsumer = stubPersonConsumer()
        val personService = PersonService(personConsumer)

        val validerBeregning = ValiderBeregning()
        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning)
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService),
                validerBeregning,
                evnevurderingService,
                personService,
            )
        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
            )
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                beregningService,
                tilgangskontrollService,
                vedtakConsumer,
                sakConsumer,
                unleash,
                vedtakGrunnlagMapper,
                vedtakTilBehandlingMapping,
            )
        every { notatOpplysningerService.opprettNotat(any()) } returns "213"
        every { grunnlagService.oppdatereGrunnlagForBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        every {
            behandlingService.oppdaterVedtakFattetStatus(
                any(),
                any(),
            )
        } returns Unit
        every { vedtakConsumer.fatteVedtak(any()) } returns OpprettVedtakResponseDto(1, emptyList())
        stubSjablonProvider()
        stubKodeverkProvider()
    }

    @Test
    fun `Skal fatte vedtak og opprette grunnlagsstruktur for en forskudd behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
            withClue("Grunnlagliste skal inneholde 83 grunnlag") {
                request.grunnlagListe shouldHaveSize 83
            }
        }

        opprettVedtakRequest.validerVedtaksdetaljer(behandling)
        opprettVedtakRequest.validerPersongrunnlag()
        opprettVedtakRequest.validerSluttberegning()
        opprettVedtakRequest.validerBosstatusPerioder()
        opprettVedtakRequest.validerInntektrapportering()

        assertSoftly(opprettVedtakRequest) {
            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.SIVILSTAND_PERIODE)) {
                shouldHaveSize(1)
                it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                val sivilstandGrunnlag = it.innholdTilObjekt<SivilstandPeriode>()
                sivilstandGrunnlag[0].sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                sivilstandGrunnlag[0].periode.fom shouldBe YearMonth.parse("2022-02")
                sivilstandGrunnlag[0].periode.til shouldBe null
            }

            assertSoftly(hentGrunnlagstype(Grunnlagstype.BEREGNET_INNTEKT, bmGrunnlag.referanse)) {
                val innhold = it!!.innholdTilObjekt<BeregnetInntekt>()
                it.gjelderReferanse.shouldBe(bmGrunnlag.referanse)
                innhold.summertMånedsinntektListe.shouldHaveSize(13)
            }
            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(3)
                assertSoftly(it[0].innholdTilObjekt<NotatGrunnlag>()) {
                    innhold shouldBe henteNotatinnhold(behandling, NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT)
                    erMedIVedtaksdokumentet shouldBe false
                    type shouldBe NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT
                }

                val notatInntekter = this.find { it.innholdTilObjekt<NotatGrunnlag>().type == NotatGrunnlag.NotatType.INNTEKT }
                notatInntekter!!.innholdTilObjekt<NotatGrunnlag>().innhold shouldBe "Inntektsbegrunnelse kun i notat"
            }

            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON) shouldHaveSize 20
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 4
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 5
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 1
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }

    @Test
    fun `Skal opprette stønadsendringer med reel mottaker`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns
            opprettSakForBehandlingMedReelMottaker(
                behandling,
            )

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe shouldHaveSize 2
            assertSoftly(stønadsendringListe[0]) {
                skyldner.verdi shouldBe "NAV"
                kravhaver.verdi shouldBe testdataBarn1.ident
                mottaker.verdi shouldBe "REEL_MOTTAKER"
            }
            assertSoftly(stønadsendringListe[1]) {
                skyldner.verdi shouldBe "NAV"
                kravhaver.verdi shouldBe testdataBarn2.ident
                mottaker.verdi shouldBe testdataBM.ident
            }
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette husstandsmedlem uten navn og bruke nyeste identer`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBarn1 = "ny_ident_barn_1"
        val nyIdentBarn2 = "ny_ident_barn_2"
        val nyIdentHusstandsmedlem = "ny_ident_husstandsmedlem"
        stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1, personConsumer)
        stubHentPersonNyIdent(testdataBarn2.ident, nyIdentBarn2, personConsumer)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, personConsumer)
        stubHentPersonNyIdent(testdataHusstandsmedlem1.ident, nyIdentHusstandsmedlem, personConsumer)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        val husstandsmedlemUtenIdent =
            Husstandsmedlem(
                behandling = behandling,
                kilde = Kilde.MANUELL,
                ident = null,
                navn = "Mr Hansen",
                fødselsdato = LocalDate.parse("2020-01-01"),
                id = 8,
            )
        husstandsmedlemUtenIdent.perioder =
            mutableSetOf(
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlemUtenIdent,
                    datoFom = behandling.søktFomDato,
                    datoTom = null,
                    bostatus = Bostatuskode.MED_FORELDER,
                    kilde = Kilde.MANUELL,
                    id = 2,
                ),
            )
        behandling.husstandsmedlem.add(husstandsmedlemUtenIdent)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE
            request.stønadsendringListe shouldHaveSize 2
        }

        assertSoftly(opprettVedtakRequest.stønadsendringListe[0]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn1
            mottaker.verdi shouldBe nyIdentBm
        }
        assertSoftly(opprettVedtakRequest.stønadsendringListe[1]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn2
            mottaker.verdi shouldBe nyIdentBm
        }
        opprettVedtakRequest.engangsbeløpListe.shouldBeEmpty()
        opprettVedtakRequest.grunnlagListe.shouldHaveSize(86)

        opprettVedtakRequest.grunnlagListe.hentAllePersoner() shouldHaveSize 7
        opprettVedtakRequest.grunnlagListe.søknadsbarn
            .toList()[0]
            .personIdent shouldBe nyIdentBarn1
        opprettVedtakRequest.grunnlagListe.søknadsbarn
            .toList()[1]
            .personIdent shouldBe nyIdentBarn2
        opprettVedtakRequest.grunnlagListe.bidragsmottaker!!.personIdent shouldBe nyIdentBm

        val husstandsmedlemmer =
            opprettVedtakRequest.grunnlagListe.hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)
        husstandsmedlemmer shouldHaveSize 4
        husstandsmedlemmer[0].personIdent shouldBe testdataHusstandsmedlem1.ident
        assertSoftly(husstandsmedlemmer[1].innholdTilObjekt<Person>()) {
            ident shouldBe null
            navn shouldBe "Mr Hansen"
            fødselsdato shouldBe LocalDate.parse("2020-01-01")
        }
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal bruke nyeste identer for avslag`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBarn1 = "ny_ident_barn_1"
        val nyIdentBarn2 = "ny_ident_barn_2"
        val nyIdentHusstandsmedlem = "ny_ident_husstandsmedlem"
        val mock = stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1)
        stubHentPersonNyIdent(testdataBarn2.ident, nyIdentBarn2, mock)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, mock)
        stubHentPersonNyIdent(testdataHusstandsmedlem1.ident, nyIdentHusstandsmedlem, mock)
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.refVedtaksid = 553
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        opprettVedtakRequest.type shouldBe Vedtakstype.FASTSETTELSE
        opprettVedtakRequest.stønadsendringListe shouldHaveSize 2

        assertSoftly(opprettVedtakRequest.stønadsendringListe[0]) {
            skyldner.verdi shouldBe "NAV"
            kravhaver.verdi shouldBe nyIdentBarn1
            omgjørVedtakId shouldBe 553
            mottaker.verdi shouldBe nyIdentBm
        }
        assertSoftly(opprettVedtakRequest.stønadsendringListe[1]) {
            skyldner.verdi shouldBe "NAV"
            omgjørVedtakId shouldBe 553
            kravhaver.verdi shouldBe nyIdentBarn2
            mottaker.verdi shouldBe nyIdentBm
        }
        opprettVedtakRequest.engangsbeløpListe.shouldBeEmpty()
        opprettVedtakRequest.grunnlagListe.shouldHaveSize(5)
        opprettVedtakRequest.grunnlagListe.hentAllePersoner() shouldHaveSize 3
        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    fun `Skal opprette grunnlagsstruktur for avslag av forskudd behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.leggTilNotat(
            "Inntektsbegrunnelse kun i notat",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Virkningstidspunkt kun i notat",
            NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
        )

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)
        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)

        val opprettVedtakRequest = opprettVedtakSlot.captured
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }

        assertSoftly(opprettVedtakRequest) { request ->
            request.type shouldBe Vedtakstype.FASTSETTELSE
            request.stønadsendringListe shouldHaveSize 2
            request.engangsbeløpListe.shouldBeEmpty()
            request.grunnlagListe shouldHaveSize 6
            assertSoftly(behandlingsreferanseListe) { behandlingRef ->
                behandlingRef shouldHaveSize 2
                with(behandlingRef[0]) {
                    kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BEHANDLING_ID
                    referanse shouldBe behandling.id.toString()
                }
                with(behandlingRef[1]) {
                    kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BISYS_SØKNAD
                    referanse shouldBe behandling.soknadsid.toString()
                }
            }

            assertSoftly(stønadsendringListe) {
                withClue("Stønadsendring søknadsbarn 1") {
                    it[0].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[0].kravhaver.verdi shouldBe behandling.søknadsbarn[0].ident
                    it[0].skyldner.verdi shouldBe "NAV"
                    it[0].grunnlagReferanseListe.shouldHaveSize(3)
                    it[0].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[0].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.virkningstidspunkt!!,
                                    null,
                                )
                            beløp shouldBe null
                            valutakode shouldBe "NOK"
                            resultatkode shouldBe Resultatkode.AVSLAG.name
                            grunnlagReferanseListe.shouldBeEmpty()
                        }
                    }
                }
                withClue("Stønadsendring søknadsbarn 2") {
                    it[1].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
                    it[1].kravhaver.verdi shouldBe behandling.søknadsbarn[1].ident
                    it[1].skyldner.verdi shouldBe "NAV"
                    it[1].grunnlagReferanseListe.shouldHaveSize(3)
                    it[1].grunnlagReferanseListe.forEach {
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
                    }
                    assertSoftly(it[1].periodeListe) {
                        shouldHaveSize(1)
                        assertSoftly(it[0]) {
                            periode shouldBe
                                ÅrMånedsperiode(
                                    behandling.virkningstidspunkt!!,
                                    null,
                                )
                            beløp shouldBe null
                            valutakode shouldBe "NOK"
                            resultatkode shouldBe Resultatkode.AVSLAG.name
                            grunnlagReferanseListe.shouldBeEmpty()
                        }
                    }
                }
                hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
                hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            }
        }
    }
}

private fun OpprettVedtakRequestDto.validerPersongrunnlag() {
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_SØKNADSBARN)) {
        shouldHaveSize(2)
        it.shouldContainPerson(testdataBarn1.ident)
        it.shouldContainPerson(testdataBarn2.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_HUSSTANDSMEDLEM)) {
        shouldHaveSize(3)
        it.shouldContainPerson(testdataHusstandsmedlem1.ident)
    }
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.PERSON_BIDRAGSMOTTAKER)) {
        shouldHaveSize(1)
        it.shouldContainPerson(testdataBM.ident)
    }
}

private fun OpprettVedtakRequestDto.validerVedtaksdetaljer(behandling: Behandling) {
    assertSoftly("Søknadsdetaljer") {
        grunnlagListe.virkningsdato shouldNotBe null
        val virkningsdato =
            grunnlagListe.virkningsdato?.innholdTilObjekt<VirkningstidspunktGrunnlag>()
        virkningsdato!!.virkningstidspunkt shouldHaveSameDayAs behandling.virkningstidspunkt!!
        virkningsdato.årsak shouldBe behandling.årsak

        grunnlagListe.søknad shouldNotBe null
        val søknad = grunnlagListe.søknad?.innholdTilObjekt<SøknadGrunnlag>()
        søknad!!.mottattDato shouldHaveSameDayAs behandling.mottattdato
        søknad.søktAv shouldBe behandling.soknadFra
        søknad.søktFraDato shouldBe behandling.søktFomDato
    }

    assertSoftly(behandlingsreferanseListe) { behandlingRef ->
        behandlingRef shouldHaveSize 2
        with(behandlingRef[0]) {
            kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BEHANDLING_ID
            referanse shouldBe behandling.id.toString()
        }
        with(behandlingRef[1]) {
            kilde shouldBe no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde.BISYS_SØKNAD
            referanse shouldBe behandling.soknadsid.toString()
        }
    }

    assertSoftly(stønadsendringListe) {
        withClue("Stønadsendring søknadsbarn 1") {
            it[0].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
            it[0].kravhaver.verdi shouldBe behandling.søknadsbarn[0].ident
            it[0].skyldner.verdi shouldBe "NAV"
            it[0].omgjørVedtakId shouldBe 553
            it[0].grunnlagReferanseListe.shouldHaveSize(5)
            it[0].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[0].periodeListe) {
                shouldHaveSize(6)
                assertSoftly(it[0]) {
                    periode shouldBe
                        ÅrMånedsperiode(
                            YearMonth.parse("2023-02"),
                            YearMonth.parse("2023-07"),
                        )
                    beløp shouldBe BigDecimal(1760)
                    valutakode shouldBe "NOK"
                    resultatkode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name
                    grunnlagReferanseListe shouldHaveSize 1
                    val grunnlag =
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = grunnlagReferanseListe[0])
                    grunnlag shouldHaveSize 1
                    grunnlag[0].type shouldBe Grunnlagstype.SLUTTBEREGNING_FORSKUDD
                }
            }
        }
        withClue("Stønadsendring søknadsbarn 2") {
            it[1].mottaker.verdi shouldBe behandling.bidragsmottaker?.ident
            it[1].kravhaver.verdi shouldBe behandling.søknadsbarn[1].ident
            it[1].skyldner.verdi shouldBe "NAV"
            it[1].omgjørVedtakId shouldBe 553
            it[1].grunnlagReferanseListe.shouldHaveSize(5)
            it[1].grunnlagReferanseListe.forEach {
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it).shouldHaveSize(1)
            }
            assertSoftly(it[1].periodeListe) {
                shouldHaveSize(6)
                assertSoftly(it[0]) {
                    periode shouldBe
                        ÅrMånedsperiode(
                            YearMonth.parse("2023-02"),
                            YearMonth.parse("2023-07"),
                        )
                    beløp shouldBe BigDecimal(1760)
                    valutakode shouldBe "NOK"
                    resultatkode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT.name
                    grunnlagReferanseListe shouldHaveSize 1
                    val grunnlag =
                        grunnlagListe.filtrerBasertPåEgenReferanse(referanse = grunnlagReferanseListe[0])
                    grunnlag shouldHaveSize 1
                    grunnlag[0].type shouldBe Grunnlagstype.SLUTTBEREGNING_FORSKUDD
                }
            }
        }
    }
}

private fun OpprettVedtakRequestDto.validerBosstatusPerioder() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)!!
    val husstandsmedlemGrunnlag = grunnlagListe.hentPerson(testdataHusstandsmedlem1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.BOSTATUS_PERIODE)) {
        shouldHaveSize(6)
        val bostatusSøknadsbarn1 =
            it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag.referanse)
        bostatusSøknadsbarn1.shouldHaveSize(2)
        it[0].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[1].gjelderReferanse shouldBe søknadsbarn1Grunnlag.referanse
        it[4].gjelderReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[5].gjelderReferanse shouldBe søknadsbarn2Grunnlag.referanse
        it[2].gjelderReferanse shouldBe husstandsmedlemGrunnlag.referanse
        it[3].gjelderReferanse shouldBe husstandsmedlemGrunnlag.referanse
        assertSoftly(bostatusSøknadsbarn1[0].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-02")
            periode.til shouldBe YearMonth.parse("2023-08")
            relatertTilPart shouldBe bmGrunnlag.referanse
        }
        assertSoftly(bostatusSøknadsbarn1[1].innholdTilObjekt<BostatusPeriode>()) {
            bostatus shouldBe Bostatuskode.IKKE_MED_FORELDER
            periode.fom shouldBe YearMonth.parse("2023-08")
            periode.til shouldBe null
            relatertTilPart shouldBe bmGrunnlag.referanse
        }

        it
            .filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag.referanse)
            .shouldHaveSize(2)
        it
            .filtrerBasertPåFremmedReferanse(referanse = husstandsmedlemGrunnlag.referanse)
            .shouldHaveSize(2)
    }
}

private fun OpprettVedtakRequestDto.validerInntektrapportering() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarnGrunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!
    assertSoftly(hentGrunnlagstyper(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)) {
        shouldHaveSize(5)
        it[0].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[1].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[2].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[3].gjelderReferanse.shouldBe(bmGrunnlag.referanse)
        it[4].gjelderReferanse.shouldBe(søknadsbarnGrunnlag.referanse)

        assertSoftly(it[0].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe YearMonth.parse("2022-07")
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 50000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[1].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-07")
            periode.til shouldBe YearMonth.parse("2022-10")
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.SAKSBEHANDLER_BEREGNET_INNTEKT
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe true
        }
        assertSoftly(it[2]) {
            it.grunnlagsreferanseListe shouldHaveSize 1
            val grunnlag =
                grunnlagListe.filtrerBasertPåEgenReferanse(referanse = it.grunnlagsreferanseListe[0])
            grunnlag[0].type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT
            assertSoftly(innholdTilObjekt<InntektsrapporteringPeriode>()) {
                periode.fom shouldBe YearMonth.parse("2022-10")
                periode.til shouldBe null
                inntekstpostListe shouldHaveSize 1
                beløp shouldBe 60000.toBigDecimal()
                inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
                gjelderBarn shouldBe null
                valgt shouldBe true
                manueltRegistrert shouldBe false
            }
        }
        assertSoftly(it[3].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.BARNETILLEGG
            gjelderBarn shouldBe søknadsbarnGrunnlag.referanse
            valgt shouldBe true
            manueltRegistrert shouldBe false
        }
        assertSoftly(it[4].innholdTilObjekt<InntektsrapporteringPeriode>()) {
            periode.fom shouldBe YearMonth.parse("2022-01")
            periode.til shouldBe null
            inntekstpostListe shouldHaveSize 0
            beløp shouldBe 60000.toBigDecimal()
            inntektsrapportering shouldBe Inntektsrapportering.AINNTEKT_BEREGNET_12MND
            gjelderBarn shouldBe null
            valgt shouldBe true
            manueltRegistrert shouldBe false
        }
    }
}

private fun OpprettVedtakRequestDto.validerSluttberegning() {
    val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
    val søknadsbarn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)
    val søknadsbarn2Grunnlag = grunnlagListe.hentPerson(testdataBarn2.ident)

    assertSoftly(hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)) {
        shouldHaveSize(12)
        it.filtrerBasertPåFremmedReferanse(referanse = søknadsbarn2Grunnlag!!.referanse) shouldHaveSize 6
    }

    val sluttberegningForskudd =
        hentGrunnlagstyper(Grunnlagstype.SLUTTBEREGNING_FORSKUDD)
            .filtrerBasertPåFremmedReferanse(referanse = søknadsbarn1Grunnlag!!.referanse)
    sluttberegningForskudd shouldHaveSize (6)

    assertSoftly(sluttberegningForskudd[4]) {
        val innhold = innholdTilObjekt<SluttberegningForskudd>()
        innhold.beløp.toBigInteger() shouldBe 1880.toBigInteger()
        innhold.resultatKode shouldBe no.nav.bidrag.domene.enums.beregning.Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
        innhold.aldersgruppe shouldBe AldersgruppeForskudd.ALDER_0_10_ÅR
    }
    val delberegningInntekt =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
            sluttberegningForskudd[3].grunnlagsreferanseListe,
        )

    delberegningInntekt shouldHaveSize (1)
    val delberegningInnhold = delberegningInntekt[0].innholdTilObjekt<DelberegningSumInntekt>()

    assertSoftly(delberegningInntekt[0]) { delberegning ->
        delberegningInnhold.totalinntekt shouldBe "120000.00".toBigDecimal()
        delberegningInnhold.skattepliktigInntekt shouldBe "60000.00".toBigDecimal()
        delberegningInnhold.barnetillegg shouldBe "60000.00".toBigDecimal()
        delberegning.grunnlagsreferanseListe shouldHaveSize 2
    }

    val delberegningInntektFiltrertPåEgenReferanse =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntekt[0].grunnlagsreferanseListe[0])
            .first()

    assertSoftly(delberegningInntektFiltrertPåEgenReferanse) {
        it.type shouldBe Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE
        it.grunnlagsreferanseListe shouldHaveSize 1
    }

    val innhentetAinntekt =
        grunnlagListe
            .filtrerBasertPåEgenReferanse(referanse = delberegningInntektFiltrertPåEgenReferanse.grunnlagsreferanseListe[0])
            .first()
    assertSoftly(innhentetAinntekt) {
        it.type shouldBe Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT
        it.grunnlagsreferanseListe shouldHaveSize 0
        it.gjelderReferanse shouldBe bmGrunnlag.referanse
    }

    val delberegningBarnIHusstand =
        hentGrunnlagstyperForReferanser(
            Grunnlagstype.DELBEREGNING_BARN_I_HUSSTAND,
            sluttberegningForskudd[4].grunnlagsreferanseListe,
        )

    assertSoftly(delberegningBarnIHusstand) {
        shouldHaveSize(1)
        assertSoftly(it[0]) { delberegning ->
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().antallBarn shouldBe 3
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.fom shouldBe
                YearMonth.parse(
                    "2023-02",
                )
            delberegning.innholdTilObjekt<DelberegningBarnIHusstand>().periode.til shouldBe YearMonth.parse("2023-08")
            delberegning.grunnlagsreferanseListe shouldHaveSize 3

            val bosstatusHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = delberegning.grunnlagsreferanseListe[0])
                    .first()
            bosstatusHusstandsmedlem.type shouldBe Grunnlagstype.BOSTATUS_PERIODE
            bosstatusHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 1

            val innhentetHusstandsmedlem =
                grunnlagListe
                    .filtrerBasertPåEgenReferanse(referanse = bosstatusHusstandsmedlem.grunnlagsreferanseListe[0])
                    .first()
            innhentetHusstandsmedlem.type shouldBe Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM
            innhentetHusstandsmedlem.grunnlagsreferanseListe shouldHaveSize 0
            innhentetHusstandsmedlem.gjelderReferanse shouldBe bmGrunnlag.referanse
        }
    }
}
