package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.TilgangskontrollService
import no.nav.bidrag.behandling.service.ValiderBehandlingService
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import stubPersonConsumer
import java.math.BigDecimal

@ExtendWith(MockKExtension::class)
class DtoMapperMockTest {
    lateinit var dtomapper: Dtomapper

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService

    @MockK
    lateinit var validerBehandlingService: ValiderBehandlingService

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
            it.manueltOverstyrtGebyr.shouldBeNull()
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(20000)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(20000)
            it.beregnetIlagtGebyr shouldBe false
            it.manueltOverstyrtGebyr.shouldBeNull()
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
            it.manueltOverstyrtGebyr.shouldNotBeNull()
            it.manueltOverstyrtGebyr.ilagtGebyr shouldBe true
            it.manueltOverstyrtGebyr.begrunnelse shouldBe "Begrunnelse"
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(500000)
            it.inntekt.maksBarnetillegg shouldBe BigDecimal(2000)
            it.inntekt.totalInntekt shouldBe BigDecimal(502000)
            it.beregnetIlagtGebyr shouldBe true
            it.manueltOverstyrtGebyr.shouldBeNull()
        }
    }

    @Test
    fun `skal ikke mappe gebyr hvis rolle ikke har gebyrsøknad`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragsmottaker!!.harGebyrsøknad = false
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        val gebyr = behandlingDto.gebyr
        gebyr.shouldNotBeNull()
        gebyr.gebyrRoller.shouldHaveSize(0)
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

        behandling.bidragsmottaker!!.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse")
        val behandlingDto = dtomapper.tilDto(behandling)

        behandlingDto.shouldNotBeNull()
        val gebyr = behandlingDto.gebyr
        gebyr.shouldNotBeNull()
        gebyr.gebyrRoller.shouldHaveSize(2)
        gebyr.valideringsfeil!!.shouldHaveSize(1)
        assertSoftly(gebyr.valideringsfeil.first()) {
            it.gjelder.rolletype shouldBe Rolletype.BIDRAGSPLIKTIG
            it.måBestemmeGebyr shouldBe true
            it.manglerBegrunnelse shouldBe false
            it.harFeil shouldBe true
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSMOTTAKER }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(90000)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(90000)
            it.beregnetIlagtGebyr shouldBe false
            it.manueltOverstyrtGebyr.shouldNotBeNull()
            it.manueltOverstyrtGebyr.ilagtGebyr shouldBe false
            it.manueltOverstyrtGebyr.begrunnelse shouldBe "Begrunnelse"
        }
        assertSoftly(gebyr.gebyrRoller.find { it.rolle.rolletype == Rolletype.BIDRAGSPLIKTIG }!!) {
            it.inntekt.skattepliktigInntekt shouldBe BigDecimal(0)
            it.inntekt.maksBarnetillegg shouldBe null
            it.inntekt.totalInntekt shouldBe BigDecimal(0)
            it.beregnetIlagtGebyr shouldBe false
            it.manueltOverstyrtGebyr.shouldBeNull()
        }
    }
}
