package no.nav.bidrag.behandling.transformers.behandling

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdVoksneRequest
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.stubPersonConsumer
import no.nav.bidrag.behandling.utils.stubSaksbehandlernavnProvider
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteUtgift
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Familierelasjon
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BehandlingDtoMappingTest : TestContainerRunner() {
    @Autowired
    lateinit var tilgangskontrollService: TilgangskontrollService

    lateinit var mapper: Dtomapper
    lateinit var bidragBeløpsistorikkConsumer: BidragBeløpshistorikkConsumer
    lateinit var validering: ValiderBeregning
    lateinit var validerBehandling: ValiderBehandlingService
    lateinit var vedtakGrunnlagsmapper: VedtakGrunnlagMapper

    @MockkBean
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    @MockkBean
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var personService: PersonService

    @BeforeEach
    fun initMocks() {
        stubPersonConsumer()
        stubKodeverkProvider()
        stubSjablonProvider()
        stubSaksbehandlernavnProvider()
        validering = ValiderBeregning()
        val personService = PersonService(stubPersonConsumer())
        validerBehandling = mockkClass(ValiderBehandlingService::class)
        every { validerBehandling.kanBehandlesINyLøsning(any()) } returns null
        vedtakGrunnlagsmapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                ValiderBeregning(),
                evnevurderingService,
                barnebidragGrunnlagInnhenting,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        mapper =
            Dtomapper(
                tilgangskontrollService,
                validering,
                validerBehandling,
                vedtakGrunnlagsmapper,
                BeregnBarnebidragApi(),
            )
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG behandling med utgifter`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Høreapparat"
        behandling.notater.add(
            Notat(
                behandling = behandling,
                innhold = "Notat utgift",
                rolle = behandling.bidragsmottaker!!,
                type = NotatGrunnlag.NotatType.UTGIFTER,
            ),
        )

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = "Batteri",
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    kommentar = null,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = "Ny mikrofon for skolelærer",
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Tar ikke med fraktkostnader",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = mapper.run { behandling.tilUtgiftDto() }

        assertSoftly(utgiftDto!!.beregning!!) {
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1000)
            totalBeløpBetaltAvBp shouldBe BigDecimal(1000)
            totalGodkjentBeløp shouldBe BigDecimal(2100)
            totalGodkjentBeløpBp shouldBe BigDecimal.ZERO
        }
        assertSoftly(utgiftDto) {
            avslag shouldBe null
            kategori.kategori shouldBe Særbidragskategori.ANNET
            kategori.beskrivelse shouldBe "Høreapparat"
            notat.innhold shouldBe "Notat utgift"
            utgifter shouldHaveSize 2
            utgifter[0].type shouldBe "Ny mikrofon for skolelærer"
            utgifter[1].type shouldBe "Batteri"
        }
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG behandling med utgifter og kategori KONFIRMASJON`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        behandling.notater.add(
            Notat(
                behandling = behandling,
                innhold = "Notat utgift",
                rolle = behandling.bidragsmottaker!!,
                type = NotatGrunnlag.NotatType.UTGIFTER,
            ),
        )

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = Utgiftstype.KLÆR.name,
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    kommentar = null,
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    betaltAvBp = true,
                    kommentar = "Inkluderer ikke alkohol",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = Utgiftstype.REISEUTGIFT.name,
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Tar ikke med bompenger",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = mapper.run { behandling.tilUtgiftDto() }

        assertSoftly(utgiftDto!!.beregning!!) {
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1000)
            totalBeløpBetaltAvBp shouldBe BigDecimal(3100)
            totalGodkjentBeløp shouldBe BigDecimal(4100)
            totalGodkjentBeløpBp shouldBe BigDecimal(2100)
        }
        assertSoftly(utgiftDto) {
            avslag shouldBe null
            kategori.kategori shouldBe Særbidragskategori.KONFIRMASJON
            notat.innhold shouldBe "Notat utgift"
            utgifter shouldHaveSize 3
            utgifter[0].type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
            utgifter[1].type shouldBe Utgiftstype.REISEUTGIFT.name
            utgifter[2].type shouldBe Utgiftstype.KLÆR.name
        }
    }

    @Test
    fun `skal mappe BehandlingDto for SÆRBIDRAG med avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.SÆRBIDRAG)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.stonadstype = null
        behandling.avslag = Resultatkode.PRIVAT_AVTALE
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.kategoriBeskrivelse = "Høreapparat"
        behandling.notater.add(
            Notat(
                behandling = behandling,
                innhold = "Notat utgift",
                rolle = behandling.bidragsmottaker!!,
                type = NotatGrunnlag.NotatType.UTGIFTER,
            ),
        )

        behandling.utgift = oppretteUtgift(behandling, "")
        behandling.utgift!!.beløpDirekteBetaltAvBp = BigDecimal.valueOf(1000)
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.now().minusDays(4),
                    type = "Batteri",
                    kravbeløp = BigDecimal(100),
                    godkjentBeløp = BigDecimal(100),
                    kommentar = null,
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    id = 2,
                    dato = LocalDate.now().minusDays(10),
                    type = "Ny mikrofon for skolelærer",
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Tar ikke med fraktkostnader",
                    utgift = behandling.utgift!!,
                ),
            )

        val utgiftDto = mapper.run { behandling.tilUtgiftDto() }

        assertSoftly(utgiftDto!!) {
            beregning shouldBe null
            avslag shouldBe Resultatkode.PRIVAT_AVTALE
            kategori.kategori shouldBe Særbidragskategori.ANNET
            kategori.beskrivelse shouldBe "Høreapparat"
            notat.innhold shouldBe "Notat utgift"
            utgifter shouldHaveSize 0
        }
    }

    @Test
    fun `mappe behandlingDto for særbidrag  med andre voksne i husstanden`() {
        // gitt
        val behandling = oppretteTestbehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG, true, true)

        val andreVoksneUbehandla =
            behandling.grunnlag
                .find { it.type == Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN && !it.erBearbeidet }
                .konvertereData<Set<RelatertPersonGrunnlagDto>>()

        val andreVoksneUbehandlaOppdatert =
            andreVoksneUbehandla?.filter { it.relasjon != Familierelasjon.INGEN }?.toMutableSet()
        val voksenIHusstanden = andreVoksneUbehandla?.find { it.relasjon == Familierelasjon.INGEN }
        andreVoksneUbehandlaOppdatert?.add(
            RelatertPersonGrunnlagDto(
                partPersonId = voksenIHusstanden!!.partPersonId!!,
                borISammeHusstandDtoListe =
                    listOf(
                        BorISammeHusstandDto(
                            voksenIHusstanden.borISammeHusstandDtoListe.minBy { it.periodeFra!! }.periodeFra?.minusMonths(
                                1,
                            ),
                            null,
                        ),
                    ),
                fødselsdato = voksenIHusstanden.fødselsdato!!,
                navn = voksenIHusstanden.navn!!,
            ),
        )
        behandling.grunnlag.add(
            Grunnlag(
                type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                erBearbeidet = false,
                rolle = behandling.bidragsmottaker!!,
                behandling = behandling,
                data = commonObjectmapper.writeValueAsString(andreVoksneUbehandlaOppdatert),
                innhentet = LocalDateTime.now(),
            ),
        )

        val behandlaAndreVoksneOppdatert =
            BoforholdApi.beregnBoforholdAndreVoksne(
                behandling.virkningstidspunktEllerSøktFomDato,
                andreVoksneUbehandlaOppdatert!!.tilBoforholdVoksneRequest(behandling),
            )
        behandling.grunnlag.add(
            Grunnlag(
                type = Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN,
                erBearbeidet = true,
                rolle = behandling.bidragspliktig!!,
                behandling = behandling,
                data = commonObjectmapper.writeValueAsString(behandlaAndreVoksneOppdatert),
                innhentet = LocalDateTime.now(),
            ),
        )

        // hvis
        val behandlingDto = mapper.tilDto(behandling, true)

        // så
        assertSoftly(behandlingDto) { b ->
            b.aktiveGrunnlagsdata.andreVoksneIHusstanden
                ?.perioder
                ?.shouldHaveSize(3)
            b.ikkeAktiverteEndringerIGrunnlagsdata.andreVoksneIHusstanden
                ?.perioder
                ?.shouldHaveSize(2)
            b.ikkeAktiverteEndringerIGrunnlagsdata.andreVoksneIHusstanden
                ?.perioder!!
                .find {
                    it.status == Bostatuskode.BOR_MED_ANDRE_VOKSNE
                }!!
                .husstandsmedlemmer shouldHaveSize 1
        }
    }
}
