package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.getunleash.FakeUnleash
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragGrunnlagConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.HentetGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.HusstandsmedlemRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.SivilstandRepository
import no.nav.bidrag.behandling.database.repository.UnderholdskostnadRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.skyldnerNav
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettStønadDto
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm
import no.nav.bidrag.behandling.utils.testdata.testdataBarnBm2
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.inntekt.InntektApi
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.stonad.response.StønadDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubBehandlingrepository
import stubHentPersonNyIdent
import stubHusstandrepository
import stubPersonConsumer
import stubPersonRepository
import stubSivilstandrepository
import stubUnderholdskostnadRepository
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class GrunnlagMockService {
    @MockkBean
    lateinit var underholdService: UnderholdService
    val notatService = NotatService()

    @MockkBean
    lateinit var grunnlagConsumer: BidragGrunnlagConsumer

    lateinit var boforholdService: BoforholdService

    @MockkBean
    lateinit var grunnlagRepository: GrunnlagRepository

    @MockkBean
    lateinit var behandlingRepository: BehandlingRepository

    @MockkBean
    lateinit var inntektService: InntektService

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var grunnlagService: GrunnlagService

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockkBean
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockkBean
    lateinit var underholdskostnadRepository: UnderholdskostnadRepository

    @MockkBean
    lateinit var husstandsmedlemRepository: HusstandsmedlemRepository

    @MockkBean
    lateinit var sivilstandRepository: SivilstandRepository

    @MockkBean
    lateinit var bidragStønadConsumer: BidragStønadConsumer

    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    lateinit var personRepository: PersonRepository
    lateinit var personConsumer: BidragPersonConsumer
    lateinit var dtomapper: Dtomapper

    @BeforeEach
    fun init() {
        val validerBeregning = ValiderBeregning()
        personRepository = stubPersonRepository()
        personConsumer = stubPersonConsumer()
        val personService = PersonService(personConsumer)
        every { bidragStønadConsumer.hentHistoriskeStønader(any()) } returns null
        barnebidragGrunnlagInnhenting = BarnebidragGrunnlagInnhenting(bidragStønadConsumer)

        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                behandlingTilGrunnlagMappingV2,
                validerBeregning,
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )

        dtomapper =
            Dtomapper(
                tilgangskontrollService,
                validerBeregning,
                validerBehandlingService,
                vedtakGrunnlagMapper,
                BeregnBarnebidragApi(),
            )
        boforholdService = BoforholdService(behandlingRepository, husstandsmedlemRepository, notatService, sivilstandRepository, dtomapper)
        underholdService =
            UnderholdService(
                underholdskostnadRepository,
                personRepository,
                notatService,
                personService,
            )
        val unleash = FakeUnleash()
        unleash.enableAll()
        grunnlagService =
            GrunnlagService(grunnlagConsumer, boforholdService, grunnlagRepository, InntektApi(""), inntektService, dtomapper, underholdService, barnebidragGrunnlagInnhenting, unleash)
        stubUnderholdskostnadRepository(underholdskostnadRepository)
        stubBehandlingrepository(behandlingRepository)
        stubHusstandrepository(husstandsmedlemRepository)
        stubSivilstandrepository(sivilstandRepository)
        every {
            grunnlagConsumer.henteGrunnlag(
                match {
                    it.any { request ->
                        request.personId != testdataBM.ident && request.personId != testdataBP.ident
                    }
                },
            )
        } returns (
            HentetGrunnlag(opprettHentGrunnlagDto())
        )
    }

    @Test
    fun `skal lagre andre barn grunnlag for bidragsmottaker`() {
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter = true,
                inkludereBp = true,
                inkludereSivilstand = false,
                behandlingstype = TypeBehandling.BIDRAG,
                inkludereVoksneIBpsHusstand = true,
                setteDatabaseider = true,
            )
        behandling.grunnlag = mutableSetOf()
        val grunnlagBarnBM =
            listOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm.ident,
                    fødselsdato = LocalDate.now().minusYears(3),
                    relasjon = Familierelasjon.INGEN,
                    navn = "Lyrisk Sopp 2323",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm.ident,
                    fødselsdato = LocalDate.now().minusYears(16),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 1",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm2.ident,
                    fødselsdato = LocalDate.now().minusYears(4),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 2",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )
        val grunnlagBarnBP =
            listOf(
                RelatertPersonGrunnlagDto(
                    gjelderPersonId = testdataBarn1.ident,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 3",
                    partPersonId = testdataBP.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    gjelderPersonId = testdataBarn2.ident,
                    fødselsdato = testdataBarn2.fødselsdato,
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 4",
                    partPersonId = testdataBP.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )
        mockGrunnlagrespons(
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBM),
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBP),
        )

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlag = behandling.grunnlag
        grunnlag shouldHaveSize 6
        grunnlag.filter { it.type == Grunnlagsdatatype.ANDRE_BARN } shouldHaveSize 1
        grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet } shouldHaveSize 2
        grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD && !it.erBearbeidet } shouldHaveSize 1
        grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && !it.erBearbeidet } shouldHaveSize 1
        grunnlag.filter { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && it.erBearbeidet } shouldHaveSize 1
        assertSoftly(grunnlag.find { it.type == Grunnlagsdatatype.ANDRE_BARN }!!) {
            it.aktiv.shouldNotBeNull()
            it.rolle.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
        }

        behandling.underholdskostnader shouldHaveSize 3
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarn1.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe null
            it.barnetsRolleIBehandlingen.shouldNotBeNull()
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarn2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe null
            it.barnetsRolleIBehandlingen.shouldNotBeNull()
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
    }

    @Test
    fun `skal lagre andre barn grunnlag for bidragsmottaker og filtrere ut barn under 12 år basert på virkningstidspunkt`() {
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter = true,
                inkludereBp = true,
                inkludereSivilstand = false,
                behandlingstype = TypeBehandling.BIDRAG,
                inkludereVoksneIBpsHusstand = true,
                setteDatabaseider = true,
            )
        behandling.virkningstidspunkt = LocalDate.now().minusYears(2).withMonth(2)
        behandling.grunnlag = mutableSetOf()
        val identBarnBM3 = "123123123123"
        val grunnlagBarnBM =
            listOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm.ident,
                    fødselsdato = LocalDate.now().minusYears(13),
                    relasjon = Familierelasjon.INGEN,
                    navn = "Lyrisk Sopp 13 år",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = identBarnBM3,
                    fødselsdato = LocalDate.now().minusYears(15),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 15 år",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm.ident,
                    fødselsdato = LocalDate.now().minusYears(14),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 14 år",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm2.ident,
                    fødselsdato = LocalDate.now().minusYears(4),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 4 år",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )
        val grunnlagBarnBP =
            listOf(
                RelatertPersonGrunnlagDto(
                    gjelderPersonId = testdataBarn1.ident,
                    fødselsdato = testdataBarn1.fødselsdato,
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 3",
                    partPersonId = testdataBP.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    gjelderPersonId = testdataBarn2.ident,
                    fødselsdato = testdataBarn2.fødselsdato,
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 4",
                    partPersonId = testdataBP.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )
        mockGrunnlagrespons(
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBM),
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBP),
        )

        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlag = behandling.grunnlag
        grunnlag shouldHaveSize 6

        behandling.underholdskostnader shouldHaveSize 4
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen.shouldBeNull()
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarn1.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe null
            it.barnetsRolleIBehandlingen.shouldNotBeNull()
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarn2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe null
            it.barnetsRolleIBehandlingen.shouldNotBeNull()
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
    }

    @Test
    fun `skal endre underholdskostnad andre barn til bidragsmottaker fra offentlig til manuell hvis ikke finnes i ny grunnlag`() {
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter = true,
                inkludereBp = true,
                inkludereSivilstand = false,
                behandlingstype = TypeBehandling.BIDRAG,
                inkludereVoksneIBpsHusstand = true,
                setteDatabaseider = true,
            )
        behandling.grunnlag = mutableSetOf()
        val grunnlagBarnBM =
            listOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm2.ident,
                    fødselsdato = LocalDate.now().minusYears(4),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 2",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )
        mockGrunnlagrespons(
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBM),
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = emptyList()),
        )
        behandling.underholdskostnader.add(
            Underholdskostnad(
                id = 3,
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                person = Person(id = 1, ident = testdataBarnBm.ident, fødselsdato = testdataBarnBm.fødselsdato),
            ),
        )
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlag = behandling.grunnlag
        grunnlag shouldHaveSize 5
        assertSoftly(grunnlag.find { it.type == Grunnlagsdatatype.ANDRE_BARN }!!) {
            it.aktiv.shouldNotBeNull()
            it.rolle.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
        }

        behandling.underholdskostnader shouldHaveSize 4
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.MANUELL
            it.barnetsRolleIBehandlingen shouldBe null
        }
    }

    @Test
    fun `skal endre underholdskostnad andre barn til bidragsmottaker fra manuell til offentlig hvis finnes i ny grunnlag`() {
        val barnOver13Ident = "123213123123"
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter = true,
                inkludereBp = true,
                inkludereSivilstand = false,
                behandlingstype = TypeBehandling.BIDRAG,
                inkludereVoksneIBpsHusstand = true,
                setteDatabaseider = true,
            )
        behandling.grunnlag = mutableSetOf()
        val grunnlagBarnBM =
            listOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm.ident,
                    fødselsdato = LocalDate.now().minusYears(8),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 1",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarnBm2.ident,
                    fødselsdato = LocalDate.now().minusYears(4),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 2",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = barnOver13Ident,
                    fødselsdato = LocalDate.now().minusYears(16),
                    relasjon = Familierelasjon.BARN,
                    navn = "Lyrisk Sopp 3",
                    partPersonId = testdataBM.ident,
                    borISammeHusstandDtoListe = emptyList(),
                ),
            )

        mockGrunnlagrespons(
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = grunnlagBarnBM),
            opprettHentGrunnlagDto().copy(husstandsmedlemmerOgEgneBarnListe = emptyList()),
        )
        behandling.underholdskostnader.add(
            Underholdskostnad(
                id = 3,
                behandling = behandling,
                kilde = Kilde.MANUELL,
                person = Person(id = 1, ident = barnOver13Ident, fødselsdato = LocalDate.now().minusYears(16)),
            ),
        )
        behandling.underholdskostnader.add(
            Underholdskostnad(
                id = 3,
                behandling = behandling,
                kilde = Kilde.MANUELL,
                person = Person(id = 1, ident = testdataBarnBm.ident, fødselsdato = testdataBarnBm.fødselsdato),
            ),
        )
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlag = behandling.grunnlag
        grunnlag shouldHaveSize 5
        assertSoftly(grunnlag.find { it.type == Grunnlagsdatatype.ANDRE_BARN }!!) {
            it.aktiv.shouldNotBeNull()
            it.rolle.rolletype shouldBe Rolletype.BIDRAGSMOTTAKER
        }

        behandling.underholdskostnader shouldHaveSize 5
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm2.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == testdataBarnBm.ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
        assertSoftly(behandling.underholdskostnader.find { it.person.ident == barnOver13Ident }) {
            it.shouldNotBeNull()
            it.kilde shouldBe Kilde.OFFENTLIG
            it.barnetsRolleIBehandlingen shouldBe null
        }
    }

    @Test
    fun `skal lagre med nyeste ident hvis endret`() {
        val nyIdentBm = "ny_ident_bm"
        val nyIdentBp = "ny_ident_bp"
        val nyIdentBarn1 = "ny_i_barn_1"
        stubHentPersonNyIdent(testdataBarn1.ident, nyIdentBarn1, personConsumer)
        stubHentPersonNyIdent(testdataBM.ident, nyIdentBm, personConsumer)
        stubHentPersonNyIdent(testdataBP.ident, nyIdentBp, personConsumer)
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter = true,
                inkludereBp = true,
                inkludereSivilstand = false,
                behandlingstype = TypeBehandling.BIDRAG,
                inkludereVoksneIBpsHusstand = true,
                setteDatabaseider = true,
            )
        behandling.virkningstidspunkt = LocalDate.now().minusYears(2).withMonth(2)
        behandling.grunnlag = mutableSetOf()
        behandling.grunnlag.add(
            Grunnlag(
                id = 1,
                behandling = behandling,
                type = Grunnlagsdatatype.BOFORHOLD,
                rolle = behandling.bidragsmottaker!!,
                gjelder = testdataBarn1.ident,
                innhentet = LocalDateTime.now(),
                data = "",
            ),
        )
        mockGrunnlagrespons(
            opprettHentGrunnlagDto(),
            opprettHentGrunnlagDto(),
        )
        val søknadsbarnId = behandling.søknadsbarn.find { it.ident == testdataBarn1.ident }
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)

        assertSoftly {
            behandling.bidragsmottaker!!.ident shouldBe nyIdentBm
            behandling.bidragspliktig!!.ident shouldBe nyIdentBp
            behandling.søknadsbarn.find { it.id == søknadsbarnId!!.id }?.ident shouldBe nyIdentBarn1

            behandling.inntekter.filter { it.ident == nyIdentBm } shouldHaveSize 3
            behandling.underholdskostnader.find { it.person.ident == nyIdentBarn1 }.shouldNotBeNull()
            behandling.grunnlag.find { it.gjelder == nyIdentBarn1 }.shouldNotBeNull()
            behandling.husstandsmedlem.find { it.ident == nyIdentBarn1 }.shouldNotBeNull()
        }
    }

    @Test
    fun `skal lagre beløpshistorikk grunnlag hvis bidrag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        mockGrunnlagrespons(
            opprettHentGrunnlagDto(),
            opprettHentGrunnlagDto(),
        )
        behandling.stonadstype = Stønadstype.BIDRAG
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), null)).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                    ),
            )
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG18AAR }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG18AAR,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null), beløp = null),
                    ),
            )
        behandling.søknadstype = BisysSøknadstype.SØKNAD
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlagsliste = behandling.grunnlag.filter { it.type in listOf(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR, Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD) }
        grunnlagsliste shouldHaveSize 1
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            konvertereData<StønadDto>().shouldNotBeNull()
            gjelder shouldBe søknadsbarn.ident
            rolle shouldBe behandling.bidragspliktig!!
            erBearbeidet shouldBe false
            aktiv.shouldNotBeNull()
        }
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.BIDRAG
                    it.skyldner.verdi shouldBe behandling.bidragspliktig!!.ident
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
        verify(exactly = 0) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.FORSKUDD
                    it.skyldner.verdi shouldBe skyldnerNav.verdi
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
        verify(exactly = 0) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.BIDRAG18AAR
                    it.skyldner.verdi shouldBe behandling.bidragspliktig!!.ident
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
    }

    @Test
    fun `skal lagre beløpshistorikk grunnlag hvis bidrag 18 år`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        mockGrunnlagrespons(
            opprettHentGrunnlagDto(),
            opprettHentGrunnlagDto(),
        )
        behandling.stonadstype = Stønadstype.BIDRAG18AAR
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), null)).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                    ),
            )
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG18AAR }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG18AAR,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2024-01-01"), null), beløp = null),
                    ),
            )
        behandling.søknadstype = BisysSøknadstype.SØKNAD
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlagsliste = behandling.grunnlag.filter { it.type in listOf(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR, Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD) }
        grunnlagsliste shouldHaveSize 2
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            konvertereData<StønadDto>().shouldNotBeNull()
            gjelder shouldBe søknadsbarn.ident
            rolle shouldBe behandling.bidragspliktig!!
            erBearbeidet shouldBe false
            aktiv.shouldNotBeNull()
        }
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR }) {
            shouldNotBeNull()
            konvertereData<StønadDto>().shouldNotBeNull()
            gjelder shouldBe søknadsbarn.ident
            rolle shouldBe behandling.bidragspliktig!!
            erBearbeidet shouldBe false
            aktiv.shouldNotBeNull()
        }

        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.BIDRAG
                    it.skyldner.verdi shouldBe behandling.bidragspliktig!!.ident
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.BIDRAG18AAR
                    it.skyldner.verdi shouldBe behandling.bidragspliktig!!.ident
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
    }

    @Test
    fun `skal lagre beløpshistorikk grunnlag hvis begrenset revurdering`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        val søknadsbarn = behandling.søknadsbarn.first()
        mockGrunnlagrespons(
            opprettHentGrunnlagDto(),
            opprettHentGrunnlagDto(),
        )
        behandling.stonadstype = Stønadstype.BIDRAG
        behandling.søknadstype = BisysSøknadstype.BEGRENSET_REVURDERING
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.FORSKUDD }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.FORSKUDD,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), null)).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                    ),
            )
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG }) } returns
            opprettStønadDto(
                stønadstype = Stønadstype.BIDRAG,
                periodeListe =
                    listOf(
                        opprettStønadPeriodeDto(ÅrMånedsperiode(LocalDate.parse("2023-01-01"), null)).copy(
                            vedtaksid = 200,
                            valutakode = "NOK",
                        ),
                    ),
            )
        every { bidragStønadConsumer.hentHistoriskeStønader(match { it.type == Stønadstype.BIDRAG18AAR }) } returns null
        grunnlagService.oppdatereGrunnlagForBehandling(behandling)
        val grunnlagsliste = behandling.grunnlag.filter { it.type in listOf(Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR, Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD) }
        grunnlagsliste shouldHaveSize 2
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG }) {
            shouldNotBeNull()
            konvertereData<StønadDto>().shouldNotBeNull()
            gjelder shouldBe søknadsbarn.ident
            rolle shouldBe behandling.bidragspliktig!!
            erBearbeidet shouldBe false
            aktiv.shouldNotBeNull()
        }
        assertSoftly(grunnlagsliste.find { it.type == Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD }) {
            shouldNotBeNull()
            konvertereData<StønadDto>().shouldNotBeNull()
            gjelder shouldBe søknadsbarn.ident
            rolle shouldBe behandling.bidragsmottaker!!
            erBearbeidet shouldBe false
            aktiv.shouldNotBeNull()
        }

        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.BIDRAG
                    it.skyldner.verdi shouldBe behandling.bidragspliktig!!.ident
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
        verify(exactly = 1) {
            bidragStønadConsumer.hentHistoriskeStønader(
                withArg {
                    it.type shouldBe Stønadstype.FORSKUDD
                    it.skyldner.verdi shouldBe skyldnerNav.verdi
                    it.sak.verdi shouldBe behandling.saksnummer
                    it.kravhaver.verdi shouldBe behandling.søknadsbarn.first().ident
                },
            )
        }
    }

    private fun mockGrunnlagrespons(
        grunnlagBM: HentGrunnlagDto,
        grunnlagBP: HentGrunnlagDto,
    ) {
        every {
            grunnlagConsumer.henteGrunnlag(
                match {
                    it.any { request ->
                        request.personId == testdataBM?.ident!!
                    }
                },
            )
        } returns (
            HentetGrunnlag(
                grunnlagBM,
            )
        )
        every {
            grunnlagConsumer.henteGrunnlag(
                match {
                    it.any { request ->
                        request.personId == testdataBP.ident!!
                    }
                },
            )
        } returns (
            HentetGrunnlag(
                grunnlagBP,
            )
        )
    }
}
