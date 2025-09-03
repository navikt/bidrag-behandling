package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import createPersonServiceMock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.dto.v1.beregning.UgyldigBeregningDto.UgyldigBeregningType
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettEvnevurderingResultat
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.behandling.utils.testdata.synkSøknadsbarnVirkningstidspunkt
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.barnebidrag.service.AldersjusteringOrchestrator
import no.nav.bidrag.beregn.barnebidrag.service.BidragsberegningOrkestrator
import no.nav.bidrag.beregn.barnebidrag.service.OmgjøringOrkestrator
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.beregn.særbidrag.BeregnSærbidragApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.grunnlag.HentGrunnlagFeiltype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningUtgift
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.søknadsbarn
import no.nav.bidrag.transport.felles.commonObjectmapper
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

@ExtendWith(SpringExtension::class)
class BeregningServiceTest {
    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var aldersjusteringOrchestrator: AldersjusteringOrchestrator

    @MockkBean
    lateinit var klageOrkestrator: OmgjøringOrkestrator

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    lateinit var vedtakGrunnlagMapper: VedtakGrunnlagMapper

    @MockkBean
    lateinit var bidragStønadConsumer: BidragBeløpshistorikkConsumer
    lateinit var bidragsberegningOrkestrator: BidragsberegningOrkestrator

    val beregnCapture = mutableListOf<BeregnGrunnlag>()

    @BeforeEach
    fun initMocks() {
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        mockkConstructor(BeregnBarnebidragApi::class)
        every { BeregnBarnebidragApi().beregn(capture(beregnCapture)) } answers { callOriginal() }
        bidragsberegningOrkestrator = BidragsberegningOrkestrator(BeregnBarnebidragApi(), klageOrkestrator)
        barnebidragGrunnlagInnhenting = BarnebidragGrunnlagInnhenting(bidragStønadConsumer)
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns null
        every { evnevurderingService.hentLøpendeBidragForBehandling(any()) } returns
            opprettEvnevurderingResultat(
                listOf(
                    testdataBarn1 to Stønadstype.BIDRAG,
                    testdataHusstandsmedlem1 to Stønadstype.BIDRAG,
                ),
            )

        val personService = createPersonServiceMock()
        val sjablonService = stubSjablonService()

        vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(sjablonService)),
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
    }

    @Test
    fun `skal bygge grunnlag for forskudd beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        mockkConstructor(BeregnForskuddApi::class)
        every { BeregnForskuddApi().beregn(capture(beregnCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneForskudd(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 2) {
            BeregnForskuddApi().beregn(any())
        }
        resultat shouldHaveSize 2
        resultat[0].resultat.grunnlagListe shouldHaveSize 40
        beregnGrunnlagList shouldHaveSize 2
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)
//            it.grunnlagListe shouldHaveSize 17

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn1.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 5
        }
        assertSoftly(beregnGrunnlagList[1]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn2.ident) shouldNotBe null

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 6

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 1

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 3
        }
    }

    @Test
    fun `skal bygge grunnlag for bidrag beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(1)
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneBidrag(1)

        verify(exactly = 1) {
            BeregnBarnebidragApi().beregn(any())
        }
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            it.ugyldigBeregning shouldBe null
            it.barn.ident!!.verdi shouldBe behandling.søknadsbarn.first().ident
            it.resultat.beregnetBarnebidragPeriodeListe shouldHaveSize 1
            it.resultat.beregnetBarnebidragPeriodeListe[0]
                .resultat.beløp shouldBe BigDecimal(4410)
        }
    }

    @Test
    fun `skal feile beregning av bidrag begrenset revurdering hvis innhenting av beløpshistorikk feiler`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(4)
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("50"),
                ),
            ),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        behandling.grunnlagsinnhentingFeilet = commonObjectmapper.writeValueAsString(mapOf(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG to GrunnlagFeilDto(grunnlagstype = null, feilmelding = "", personId = "", feiltype = HentGrunnlagFeiltype.TEKNISK_FEIL)))
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneBidrag(1)

        verify(exactly = 1) {
            BeregnBarnebidragApi().beregn(any())
        }
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            it.ugyldigBeregning shouldNotBe null
            it.ugyldigBeregning!!.tittel shouldBe "Innhenting av beløpshistorikk feilet"
            it.ugyldigBeregning!!.begrunnelse shouldContain "Det skjedde en feil ved innhenting av beløpshistorikk for forskudd og bidrag. "
            it.ugyldigBeregning!!.perioder shouldHaveSize 0
        }
    }

    @Test
    fun `skal feile beregning av bidrag begrenset revurdering hvis lavere enn løpende bidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(4)
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("5600"),
                ),
            ),
        )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(4), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneBidrag(1)

        verify(exactly = 1) {
            BeregnBarnebidragApi().beregn(any())
        }
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            it.ugyldigBeregning shouldNotBe null
            it.ugyldigBeregning!!.begrunnelse shouldContain "er lik eller lavere enn løpende bidrag"
            it.ugyldigBeregning!!.perioder shouldHaveSize 2
            it.ugyldigBeregning!!.resultatPeriode[0].type shouldBe UgyldigBeregningType.BEGRENSET_REVURDERING_LIK_ELLER_LAVERE_ENN_LØPENDE_BIDRAG
            it.resultat.grunnlagListe
                .filter { it.type == Grunnlagstype.BELØPSHISTORIKK_FORSKUDD }
                .size shouldBe 1
            it.resultat.grunnlagListe
                .filter { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG }
                .size shouldBe 1

            it.barn.ident!!.verdi shouldBe behandling.søknadsbarn.first().ident
            it.resultat.beregnetBarnebidragPeriodeListe shouldHaveSize 2
            it.resultat.beregnetBarnebidragPeriodeListe[0]
                .resultat.beløp shouldBe BigDecimal(2600)
        }
    }

    @Test
    fun `skal feile beregning av bidrag begrenset revurdering hvis ingen forskudd`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING

        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1, medId = true)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), medId = true)
        behandling.leggTilNotat(
            "Samvær",
            NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(4)
        behandling.synkSøknadsbarnVirkningstidspunkt()
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(LocalDate.now().minusMonths(2), null),
                    beløp = BigDecimal("2600"),
                ),
            ),
        )
        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneBidrag(1)

        verify(exactly = 1) {
            BeregnBarnebidragApi().beregn(any())
        }
        resultat shouldHaveSize 1
        assertSoftly(resultat[0]) {
            it.ugyldigBeregning shouldNotBe null
            it.ugyldigBeregning!!.begrunnelse shouldContain "har ingen løpende forskudd"
            it.ugyldigBeregning!!.perioder shouldHaveSize 1
            it.ugyldigBeregning!!.resultatPeriode[0].type shouldBe UgyldigBeregningType.BEGRENSET_REVURDERING_UTEN_LØPENDE_FORSKUDD
            it.resultat.grunnlagListe
                .filter { it.type == Grunnlagstype.BELØPSHISTORIKK_FORSKUDD }
                .size shouldBe 1
            it.resultat.grunnlagListe
                .filter { it.type == Grunnlagstype.BELØPSHISTORIKK_BIDRAG }
                .size shouldBe 1

            it.barn.ident!!.verdi shouldBe behandling.søknadsbarn.first().ident
            it.resultat.beregnetBarnebidragPeriodeListe shouldHaveSize 2
            it.resultat.beregnetBarnebidragPeriodeListe[0]
                .resultat.beløp shouldBe BigDecimal(0)
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.utgift!!.maksGodkjentBeløp = null
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = false
        behandling.utgift!!.utgiftsposter.add(
            Utgiftspost(
                dato = LocalDate.now().minusDays(3),
                type = Utgiftstype.KONFIRMASJONSLEIR.name,
                kravbeløp = BigDecimal(3000),
                godkjentBeløp = BigDecimal(2500),
                kommentar = "Trekker fra alkohol",
                utgift = behandling.utgift!!,
            ),
        )
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneSærbidrag(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.FASTSETTELSE
        beregnGrunnlagList shouldHaveSize 1
        assertSoftly(beregnGrunnlagList[0]) {
            it.periode.fom shouldBe YearMonth.from(behandling.virkningstidspunkt)
            it.periode.til shouldBe YearMonth.now().plusMonths(1)

            val personer =
                it.grunnlagListe.hentAllePersoner() as Collection<GrunnlagDto>
            personer shouldHaveSize 4
            personer.hentPerson(testdataBarn1.ident) shouldNotBe null
            personer.map { it.type } shouldContainAll
                listOf(
                    Grunnlagstype.PERSON_SØKNADSBARN,
                    Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
                    Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
                    Grunnlagstype.PERSON_HUSSTANDSMEDLEM,
                )

            val bostatuser =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
            bostatuser shouldHaveSize 3
            val barnStatus = bostatuser.find { it.gjelderBarnReferanse == grunnlagListe.søknadsbarn.first().referanse }
            barnStatus!!.innholdTilObjekt<BostatusPeriode>().bostatus shouldBe Bostatuskode.MED_FORELDER

            val andreVoksneIHusstanden = bostatuser.find { it.gjelderReferanse == grunnlagListe.bidragspliktig!!.referanse && it.gjelderBarnReferanse == null }
            andreVoksneIHusstanden!!.innholdTilObjekt<BostatusPeriode>().bostatus shouldBe Bostatuskode.BOR_MED_ANDRE_VOKSNE

            val sivilstand =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
            sivilstand shouldHaveSize 0

            val inntekter =
                it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            inntekter shouldHaveSize 2
            inntekter.find { it.gjelderReferanse == grunnlagListe.bidragspliktig!!.referanse } shouldNotBe null
            inntekter.find { it.gjelderReferanse == grunnlagListe.bidragsmottaker!!.referanse } shouldNotBe null

            val delberegningUtgift = it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_UTGIFT).first()
            delberegningUtgift.innholdTilObjekt<DelberegningUtgift>().sumGodkjent shouldBe BigDecimal(5000)
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning med maks godkjent beløp tatt med`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.utgift!!.utgiftsposter.add(
            Utgiftspost(
                dato = LocalDate.now().minusDays(3),
                type = Utgiftstype.KONFIRMASJONSLEIR.name,
                kravbeløp = BigDecimal(3000),
                godkjentBeløp = BigDecimal(2500),
                kommentar = "Trekker fra alkohol",
                utgift = behandling.utgift!!,
            ),
        )
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(3000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Maks godkjent beløp"
        behandling.utgift!!.maksGodkjentBeløpTaMed = true
        behandling.vedtakstype = Vedtakstype.FASTSETTELSE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneSærbidrag(1)
        val beregnGrunnlagList: List<BeregnGrunnlag> = beregnCapture

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.FASTSETTELSE
        resultat.grunnlagListe shouldHaveSize 32 // TODO:VERIFY THIS
        beregnGrunnlagList shouldHaveSize 1
        assertSoftly(beregnGrunnlagList[0]) {
            val delberegningUtgift = it.grunnlagListe.filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_UTGIFT).first()
            delberegningUtgift.innholdTilObjekt<DelberegningUtgift>().sumGodkjent shouldBe BigDecimal(3000)
        }
    }

    @Test
    fun `skal bygge grunnlag for særbidrag beregning med opprinnelig vedtakstype`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.omgjøringsdetaljer =
            Omgjøringsdetaljer(
                opprinneligVedtakstype = Vedtakstype.ENDRING,
            )
        behandling.vedtakstype = Vedtakstype.KLAGE
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()
        mockkConstructor(BeregnSærbidragApi::class)
        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }
        val resultat = BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneSærbidrag(1)

        verify(exactly = 1) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        resultat shouldNotBe null
        vedtaksTypeCapture.captured shouldBe Vedtakstype.ENDRING
    }

    @Test
    fun `skal feile hvis ugyldig behandling for særbidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.utgift = oppretteUtgift(behandling, Utgiftstype.KLÆR.name)
        behandling.virkningstidspunkt = LocalDate.now().minusMonths(2).withDayOfMonth(1)
        behandling.grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            ).toMutableSet()

        every { behandlingService.hentBehandlingById(any()) } returns behandling

        val beregnCapture = mutableListOf<BeregnGrunnlag>()
        mockkConstructor(BeregnSærbidragApi::class)
        val vedtaksTypeCapture = CapturingSlot<Vedtakstype>()

        every { BeregnSærbidragApi().beregn(capture(beregnCapture), capture(vedtaksTypeCapture)) } answers { callOriginal() }

        val exception =
            assertThrows<HttpClientErrorException> { BeregningService(behandlingService, vedtakGrunnlagMapper, aldersjusteringOrchestrator, bidragsberegningOrkestrator).beregneSærbidrag(1) }
        verify(exactly = 0) {
            BeregnSærbidragApi().beregn(any(), any())
        }
        exception.message shouldContain "Feil ved validering av behandling for beregning av særbidrag"
    }
}
