package no.nav.bidrag.behandling.service

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.ManueltOverstyrGebyrDto
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterManueltGebyrDto
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.client.HttpClientErrorException
import stubPersonConsumer
import java.math.BigDecimal

@ExtendWith(MockKExtension::class)
class GebyrServiceTest {
    @MockK
    lateinit var evnevurderingService: BeregningEvnevurderingService
    lateinit var gebyrService: GebyrService
    lateinit var vedtakGrunnlagMapper: VedtakGrunnlagMapper

    @BeforeEach
    fun initMocks() {
        val personService = PersonService(stubPersonConsumer())
        stubSjablonProvider()
        vedtakGrunnlagMapper =
            VedtakGrunnlagMapper(
                BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(stubSjablonService())),
                ValiderBeregning(),
                evnevurderingService,
                personService,
                BeregnGebyrApi(stubSjablonService()),
            )
        gebyrService = GebyrService(vedtakGrunnlagMapper)
    }

    @Test
    fun `skal oppdatere gebyr`() {
        val behandling = opprettBehandlingForGebyrberegning()
        val bm = behandling.bidragsmottaker!!
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterManueltGebyrDto(
                rolleId = bm.id!!,
                overstyrtGebyr = ManueltOverstyrGebyrDto(begrunnelse = "Begrunnelse"),
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal feile hvis det settes gebyr for en rolle som ikke har gebyrsøknad`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        val exception =
            assertThrows<HttpClientErrorException> {
                gebyrService.oppdaterManueltOverstyrtGebyr(
                    behandling,
                    OppdaterManueltGebyrDto(
                        rolleId = bm.id!!,
                        overstyrtGebyr = ManueltOverstyrGebyrDto(ilagtGebyr = false, begrunnelse = "Begrunnelse"),
                    ),
                )
            }
        exception.message shouldContain "Kan ikke sette gebyr til samme som beregnet gebyr når det ikke er avslag"
    }

    @Test
    fun `skal feile hvis gebyrvalg blir satt hvis ikke avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = false
        val exception =
            assertThrows<HttpClientErrorException> {
                gebyrService.oppdaterManueltOverstyrtGebyr(
                    behandling,
                    OppdaterManueltGebyrDto(
                        rolleId = bm.id!!,
                        overstyrtGebyr = ManueltOverstyrGebyrDto(begrunnelse = "Begrunnelse"),
                    ),
                )
            }
        exception.message shouldContain "Kan ikke endre gebyr på en rolle som ikke har gebyrsøknad"
    }

    @Test
    fun `skal oppdatere gebyr når inntekt er under gebyr grense`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterManueltGebyrDto(
                rolleId = bm.id!!,
                overstyrtGebyr = ManueltOverstyrGebyrDto(begrunnelse = "Begrunnelse"),
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal fjerne manuelt overstyrt gebyr`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(overstyrGebyr = true, ilagtGebyr = true, begrunnelse = "Begrunnelse")
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterManueltGebyrDto(
                rolleId = bm.id!!,
                overstyrtGebyr = null,
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal kreve å sette gebyrvalg hvis avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        val exception =
            assertThrows<HttpClientErrorException> {
                gebyrService.oppdaterManueltOverstyrtGebyr(
                    behandling,
                    OppdaterManueltGebyrDto(
                        rolleId = bm.id!!,
                        overstyrtGebyr = ManueltOverstyrGebyrDto(begrunnelse = "Begrunnelse"),
                    ),
                )
            }
        exception.message shouldContain "Må sette gebyr hvis det er avslag"
    }

    @Test
    fun `skal sette gebyrvalg og begrunnelse ved avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterManueltGebyrDto(
                rolleId = bm.id!!,
                overstyrtGebyr = ManueltOverstyrGebyrDto(ilagtGebyr = false, begrunnelse = "Begrunnelse"),
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal oppdatere gebyr når det endres til avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe null
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe null
    }

    @Test
    fun `skal oppdatere gebyr når det endres til ikke avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse")
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal oppdatere gebyr når det endres til ikke avslag og sette ilagtGebyr basert på beregning`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(1000000000))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse")
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }
}

private fun opprettBehandlingForGebyrberegning(inntektBeløp: BigDecimal = BigDecimal(500000)): Behandling {
    val behandling = opprettGyldigBehandlingForBeregningOgVedtak(generateId = true, typeBehandling = TypeBehandling.BIDRAG)
    behandling.bidragsmottaker!!.harGebyrsøknad = true
    behandling.inntekter.add(
        Inntekt(
            belop = inntektBeløp,
            datoFom = behandling.virkningstidspunkt,
            datoTom = null,
            ident = behandling.bidragsmottaker!!.ident!!,
            taMed = true,
            kilde = Kilde.MANUELL,
            behandling = behandling,
            type = Inntektsrapportering.PERSONINNTEKT_EGNE_OPPLYSNINGER,
            id = 2,
        ),
    )
    return behandling
}
