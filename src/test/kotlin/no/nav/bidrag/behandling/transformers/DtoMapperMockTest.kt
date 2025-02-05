package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteTestbehandling
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.assertj.core.error.ShouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class DtoMapperMockTest {
    lateinit var dtomapper: Dtomapper

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

    @MockK
    lateinit var barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting

    @BeforeEach
    fun init() {
        val personService = PersonService(stubPersonConsumer())
        val validerBeregning = ValiderBeregning()
        val behandlingTilGrunnlagMappingV2 = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService()))
        val vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                behandlingTilGrunnlagMappingV2,
                ValiderBeregning(),
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

        stubPersonConsumer()
        stubSjablonProvider()
        every { validerBehandlingService.kanBehandlesINyLøsning(any()) } returns null
        every { tilgangskontrollService.harBeskyttelse(any()) } returns false
        every { tilgangskontrollService.harTilgang(any(), any()) } returns true
    }

    @Test
    fun `skal hente og mappe gebyr`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.inntekter =
            mutableSetOf(
                Inntekt(
                    belop = BigDecimal(900000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(20000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragspliktig!!.ident!!,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(20000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    gjelderBarn = behandling.søknadsbarn.first().ident,
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    id = 1,
                ),
                Inntekt(
                    belop = BigDecimal(10000),
                    datoFom = behandling.virkningstidspunkt,
                    datoTom = null,
                    ident = behandling.bidragsmottaker!!.ident!!,
                    gjelderBarn = "123123123",
                    taMed = true,
                    kilde = Kilde.MANUELL,
                    behandling = behandling,
                    type = Inntektsrapportering.BARNETILLEGG,
                    id = 1,
                ),
            )
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        val gebyr = behandlingDto.gebyr
        gebyr.shouldNotBeNull()
        gebyr.gebyrRoller.shouldHaveSize(2)
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(900000)
            it.inntekt.maksBarnetillegg shouldBe BigDecimal(20000)
            it.inntekt.totalInntekt shouldBe BigDecimal(920000)
            it.beregnetIlagtGebyr shouldBe true
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(20000)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(20000)
            it.beregnetIlagtGebyr shouldBe false
        }
    }

    @Test
    fun `skal hente og mappe manuelt overstyrt gebyr`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(2000),
                datoFom = behandling.virkningstidspunkt,
                datoTom = null,
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = false,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(2000),
                datoFom = behandling.virkningstidspunkt,
                datoTom = null,
                ident = behandling.bidragspliktig!!.ident!!,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )
        behandling.bidragsmottaker!!.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, true, "Begrunnelse")
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        val gebyr = behandlingDto.gebyr
        gebyr.shouldNotBeNull()
        gebyr.gebyrRoller.shouldHaveSize(2)
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(50000)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(50000)
            it.beregnetIlagtGebyr shouldBe false
            it.endeligIlagtGebyr shouldBe true
            it.begrunnelse shouldBe "Begrunnelse"
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(500000)
            it.inntekt.maksBarnetillegg shouldBe BigDecimal(2000)
            it.inntekt.totalInntekt shouldBe BigDecimal(502000)
            it.beregnetIlagtGebyr shouldBe true
        }
    }

    @Test
    fun `skal ikke mappe gebyr hvis rolle ikke har gebyrsøknad`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragsmottaker!!.harGebyrsøknad = false
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        behandlingDto.gebyr.shouldBeNull()
    }

    @Test
    fun `skal hente og mappe gebyr ved avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.avslag = Resultatkode.AVSLAG
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(2000),
                datoFom = behandling.virkningstidspunkt,
                datoTom = null,
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = true,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(90000),
                datoFom = behandling.virkningstidspunkt,
                datoTom = null,
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = false,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND,
                id = 1,
            ),
        )

        behandling.bidragsmottaker!!.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(false, false, "Begrunnelse")
        behandling.bidragspliktig!!.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, true, null)
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        val gebyr = behandlingDto.gebyr
        gebyr.shouldNotBeNull()
        gebyr.gebyrRoller.shouldHaveSize(2)
        gebyr.valideringsfeil!!.shouldHaveSize(1)
        assertSoftly(gebyr.valideringsfeil.first()) {
            it.gjelder.rolletype shouldBe Rolletype.BIDRAGSPLIKTIG
            it.manglerBegrunnelse shouldBe true
            it.harFeil shouldBe true
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(90000)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(90000)
            it.endeligIlagtGebyr shouldBe false
            it.beregnetIlagtGebyr shouldBe false
            it.erManueltOverstyrt shouldBe false
            it.begrunnelse shouldBe null
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(0)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(0)
            it.beregnetIlagtGebyr shouldBe false
            it.endeligIlagtGebyr shouldBe true
            it.begrunnelse shouldBe null
        }
    }

    @Test
    fun `skal legge til informasjon om tilleggsstønad`() {
        // gitt
        val behandling =
            oppretteTestbehandling(
                setteDatabaseider = true,
                inkludereBp = true,
                behandlingstype = TypeBehandling.BIDRAG,
            )

        val innhentetForRolle = behandling.bidragsmottaker!!
        val tilleggsstønadsgrunnlag = TilleggsstønadGrunnlagDto(innhentetForRolle.personident!!.verdi, true)
        val innhentetGrunnlag =
            Grunnlag(
                behandling,
                Grunnlagsdatatype.TILLEGGSSTØNAD,
                false,
                commonObjectmapper.writeValueAsString(setOf(tilleggsstønadsgrunnlag)),
                LocalDateTime.now(),
                rolle = innhentetForRolle,
            )
        behandling.grunnlag.add(innhentetGrunnlag)

        every { validerBehandlingService.kanBehandlesINyLøsning(any()) } returns null

        // hvis
        val dto = dtomapper.tilDto(behandling)

        // så
        assertSoftly(dto.roller.find { Rolletype.BIDRAGSMOTTAKER == it.rolletype }) {
            ShouldNotBeNull.shouldNotBeNull()
            it!!.harInnvilgetTilleggsstønad shouldNotBe null
            it.harInnvilgetTilleggsstønad shouldBe true
        }
    }

    @Test
    fun `skal vise månedsinntekt`() {
        // gitt
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(2000),
                datoFom = behandling.virkningstidspunkt?.plusMonths(1),
                datoTom = null,
                ident = behandling.bidragsmottaker!!.ident!!,
                taMed = false,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )
        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(0),
                datoFom = behandling.virkningstidspunkt?.plusMonths(2),
                datoTom = null,
                ident = behandling.bidragspliktig!!.ident!!,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )

        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(-1),
                datoFom = behandling.virkningstidspunkt?.plusMonths(2),
                datoTom = null,
                ident = behandling.bidragspliktig!!.ident!!,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )

        behandling.inntekter.add(
            Inntekt(
                belop = BigDecimal(144),
                datoFom = behandling.virkningstidspunkt?.plusMonths(3),
                datoTom = null,
                ident = behandling.bidragspliktig!!.ident!!,
                gjelderBarn = behandling.søknadsbarn.first().ident,
                taMed = true,
                kilde = Kilde.MANUELL,
                behandling = behandling,
                type = Inntektsrapportering.BARNETILLEGG,
                id = 1,
            ),
        )

        // hvis
        val behandlingDto = dtomapper.tilDto(behandling)

        // så
        behandlingDto.shouldNotBeNull()

        val barnetillegg = behandlingDto.inntekter.barnetillegg shouldHaveSize 3

        assertSoftly(barnetillegg) { bt ->
            bt.forEach {
                when (it.beløp) {
                    BigDecimal(-1) -> it.månedsbeløp shouldBe BigDecimal.ZERO
                    BigDecimal.ZERO -> it.månedsbeløp shouldBe BigDecimal.ZERO
                    BigDecimal(144) -> it.månedsbeløp shouldBe BigDecimal(12)
                    BigDecimal(2000) -> it.månedsbeløp shouldBe BigDecimal(167)
                }
            }
        }
    }
}
