package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilVedtakMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.hentGrunnlagstyper
import no.nav.bidrag.behandling.utils.hentPerson
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.initGrunnlagRespons
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetillegg
import no.nav.bidrag.behandling.utils.testdata.leggTilBarnetilsyn
import no.nav.bidrag.behandling.utils.testdata.leggTilFaktiskTilsynsutgift
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.leggTilSamvær
import no.nav.bidrag.behandling.utils.testdata.leggTilTillegsstønad
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettSakForBehandling
import no.nav.bidrag.behandling.utils.testdata.taMedInntekt
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.OpprettVedtakResponseDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import stubPersonConsumer
import stubTokenUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class VedtakserviceTest : TestContainerRunner() {
    protected val testNotatJournalpostId = "123123123"
    protected val testVedtakResponsId = 1

    @Autowired
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var notatOpplysningerService: NotatOpplysningerService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @SpykBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var sakConsumer: BidragSakConsumer

    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @Autowired
    lateinit var sivilstandRepository: SivilstandRepository

    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockBean
    lateinit var bidragPersonConsumer: BidragPersonConsumer

    @Autowired
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @Autowired
    lateinit var entityManager: EntityManager

    lateinit var vedtakService: VedtakService
    lateinit var beregningService: BeregningService

    @MockK
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockK
    lateinit var personRepository: PersonRepository

    val unleash = FakeUnleash()
    val notatService = NotatService()

    @BeforeEach
    fun initMocks() {
        clearAllMocks()
        stubTokenUtils()
        unleash.enableAll()
        bidragPersonConsumer = stubPersonConsumer()
        val personService = PersonService(bidragPersonConsumer)
        val validerBeregning = ValiderBeregning()
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                VedtakGrunnlagMapper(behandlingTilGrunnlagMappingV2, validerBeregning, evnevurderingService, personService),
                BeregnBarnebidragApi(),
            )
        val underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                dtomapper,
            )
        val vedtakTilBehandlingMapping = VedtakTilBehandlingMapping(validerBeregning, underholdService)
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                validerBeregning,
                evnevurderingService,
                personService,
            )

        beregningService =
            BeregningService(
                behandlingService,
                vedtakGrunnlagMapper,
            )
        val behandlingTilVedtakMapping = BehandlingTilVedtakMapping(sakConsumer, vedtakGrunnlagMapper, beregningService)
        vedtakService =
            VedtakService(
                behandlingService,
                grunnlagService,
                notatOpplysningerService,
                tilgangskontrollService,
                vedtakConsumer,
                unleash,
                validerBeregning,
                vedtakTilBehandlingMapping,
                behandlingTilVedtakMapping,
                validerBehandlingService,
            )
        every { notatOpplysningerService.opprettNotat(any()) } returns testNotatJournalpostId
        every { tilgangskontrollService.sjekkTilgangPersonISak(any(), any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangBehandling(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangVedtak(any()) } returns Unit
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        stubUtils.stubFatteVedtak()
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidraBBMHentBeregning()
        stubUtils.stubBidragStonadLøpendeSaker()
        stubUtils.stubBidragStønaderForSkyldner()
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak for forskudd`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.FORSKUDD)
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.now().withDayOfMonth(1)
        behandling.engangsbeloptype = null
        behandling.stonadstype = Stønadstype.FORSKUDD
        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)
        behandling.initGrunnlagRespons(stubUtils)

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_3MND)

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
            request.engangsbeløpListe shouldHaveSize 0
            request.stønadsendringListe[0].type shouldBe Stønadstype.FORSKUDD
            request.stønadsendringListe[1].type shouldBe Stønadstype.FORSKUDD
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
    }

    @Test
    @Transactional
    fun `Skal fatte vedtak for bidrag behandling`() {
        stubPersonConsumer()
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(false, typeBehandling = TypeBehandling.BIDRAG)
        behandling.leggTilNotat(
            "Notat samvær BARN",
            NotatGrunnlag.NotatType.SAMVÆR,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Notat underholdskostnad BARN",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.søknadsbarn.first(),
        )
        behandling.leggTilNotat(
            "Notat underholdskostnad Andre barn",
            NotatGrunnlag.NotatType.UNDERHOLDSKOSTNAD,
            behandling.bidragsmottaker,
        )
        behandling.leggTilNotat(
            "Notat inntekt BM",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragsmottaker!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BP",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.bidragspliktig!!,
        )
        behandling.leggTilNotat(
            "Notat inntekt BA",
            NotatGrunnlag.NotatType.INNTEKT,
            behandling.søknadsbarn.first()!!,
        )
        behandling.leggTilNotat(
            "Boforhold",
            NotatGrunnlag.NotatType.BOFORHOLD,
        )
        behandling.refVedtaksid = 553
        behandling.klageMottattdato = LocalDate.now()
        behandling.inntekter = mutableSetOf()
        behandling.grunnlag = mutableSetOf()
        behandling.virkningstidspunkt = LocalDate.parse("2024-01-01")
        behandling.søknadsbarn.first().innbetaltBeløp = BigDecimal(500)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!, behandling.virkningstidspunkt!!.plusMonths(1)), samværsklasse = Samværsklasse.SAMVÆRSKLASSE_1)
        behandling.leggTilSamvær(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null))
        behandling.leggTilTillegsstønad(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(4), null))
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null), testdataHusstandsmedlem1)
        behandling.leggTilFaktiskTilsynsutgift(ÅrMånedsperiode(behandling.virkningstidspunkt!!, null))
        behandling.leggTilBarnetilsyn(ÅrMånedsperiode(behandling.virkningstidspunkt!!.plusMonths(1), null))
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragsmottaker!!)
        behandling.leggTilBarnetillegg(testdataBarn1, behandling.bidragspliktig!!)

        testdataManager.lagreBehandling(behandling)
        stubUtils.stubHentePersoninfo(personident = behandling.bidragsmottaker!!.ident!!)

        behandling.initGrunnlagRespons(stubUtils)
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        entityManager.flush()
        entityManager.refresh(behandling)
        behandling.taMedInntekt(behandling.bidragsmottaker!!, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)
        behandling.taMedInntekt(behandling.bidragspliktig!!, Inntektsrapportering.AINNTEKT_BEREGNET_12MND)

        every { sakConsumer.hentSak(any()) } returns opprettSakForBehandling(behandling)

        val opprettVedtakSlot = slot<OpprettVedtakRequestDto>()
        every { vedtakConsumer.fatteVedtak(capture(opprettVedtakSlot)) } returns
            OpprettVedtakResponseDto(
                1,
                emptyList(),
            )

        vedtakService.fatteVedtak(behandling.id!!)
        entityManager.flush()
        entityManager.refresh(behandling)
        val opprettVedtakRequest = opprettVedtakSlot.captured

        assertSoftly(behandling) {
            vedtaksid shouldBe testVedtakResponsId
            vedtakstidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            vedtakFattetAv shouldBe SAKSBEHANDLER_IDENT
        }

        assertSoftly(opprettVedtakRequest) {
            val request = opprettVedtakRequest
            request.type shouldBe Vedtakstype.FASTSETTELSE

            request.stønadsendringListe.shouldHaveSize(1)
            request.engangsbeløpListe shouldHaveSize 3
            withClue("Grunnlagliste skal inneholde ${request.grunnlagListe.size} grunnlag") {
                request.grunnlagListe shouldHaveSize 176
            }
        }

        val grunnlagsliste = opprettVedtakRequest.grunnlagListe
        assertSoftly(opprettVedtakRequest.stønadsendringListe[0]) {
            it.type shouldBe Stønadstype.BIDRAG
            it.sak shouldBe Saksnummer(behandling.saksnummer)
            it.skyldner shouldBe Personident(behandling.bidragspliktig!!.ident!!)
            it.kravhaver shouldBe Personident(behandling.søknadsbarn.first().ident!!)
            it.mottaker shouldBe Personident(behandling.bidragsmottaker!!.ident!!)
            it.innkreving shouldBe Innkrevingstype.MED_INNKREVING
            it.beslutning shouldBe Beslutningstype.ENDRING

            it.periodeListe shouldHaveSize 6
            it.grunnlagReferanseListe shouldHaveSize 9
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.NOTAT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                7
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.SØKNAD,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1
            grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                it.grunnlagReferanseListe,
            ) shouldHaveSize
                1

            assertSoftly(it.periodeListe[0]) {
                grunnlagsliste.finnGrunnlagSomErReferertFraGrunnlagsreferanseListe(
                    Grunnlagstype.SLUTTBEREGNING_BARNEBIDRAG,
                    it.grunnlagReferanseListe,
                ) shouldHaveSize
                    1
            }
        }
        assertSoftly(opprettVedtakRequest) {
            val bmGrunnlag = grunnlagListe.hentPerson(testdataBM.ident)!!
            val bpGrunnlag = grunnlagListe.hentPerson(testdataBP.ident)!!
            val barn1Grunnlag = grunnlagListe.hentPerson(testdataBarn1.ident)!!

            assertSoftly(hentGrunnlagstyper(Grunnlagstype.NOTAT)) {
                shouldHaveSize(7)
            }

            hentGrunnlagstyper(Grunnlagstype.TILLEGGSSTØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.FAKTISK_UTGIFT_PERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSPERIODE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE_NETTER) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SAMVÆRSKALKULATOR) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.VIRKNINGSTIDSPUNKT) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.SØKNAD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.BEREGNET_INNTEKT) shouldHaveSize 3 // TODO: Hvorfor 3?
            hentGrunnlagstyper(Grunnlagstype.SJABLON_SJABLONTALL) shouldHaveSize 22
            hentGrunnlagstyper(Grunnlagstype.SJABLON_BIDRAGSEVNE) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.SJABLON_TRINNVIS_SKATTESATS) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE) shouldHaveSize 8
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_AINNTEKT)
                .find { it.gjelderReferanse == barn1Grunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG) shouldHaveSize 2
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bmGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG)
                .find { it.gjelderReferanse == bpGrunnlag.referanse } shouldNotBe null
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD) shouldHaveSize 3
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_ANDRE_VOKSNE_I_HUSSTANDEN) shouldHaveSize 1
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM) shouldHaveSize 11
            hentGrunnlagstyper(Grunnlagstype.INNHENTET_SIVILSTAND) shouldHaveSize 0
        }

        verify(exactly = 1) {
            vedtakConsumer.fatteVedtak(any())
        }
        verify(exactly = 1) { notatOpplysningerService.opprettNotat(any()) }
    }
}
