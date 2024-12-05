package no.nav.bidrag.behandling.service

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.dto.v2.gebyr.OppdaterGebyrDto
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
            OppdaterGebyrDto(
                rolleId = bm.id!!,
                overstyrGebyr = true,
                begrunnelse = "Begrunnelse",
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
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
                    OppdaterGebyrDto(
                        rolleId = bm.id!!,
                        overstyrGebyr = true,
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
            OppdaterGebyrDto(
                rolleId = bm.id!!,
                overstyrGebyr = true,
                begrunnelse = "Begrunnelse",
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal fjerne manuelt overstyrt gebyr`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(overstyrGebyr = true, ilagtGebyr = true, beregnetIlagtGebyr = false, begrunnelse = "Begrunnelse")
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterGebyrDto(
                rolleId = bm.id!!,
                overstyrGebyr = false,
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal ikke kunne sette begrunnelse hvis ikke overstyrt`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        val exception =
            assertThrows<HttpClientErrorException> {
                gebyrService.oppdaterManueltOverstyrtGebyr(
                    behandling,
                    OppdaterGebyrDto(
                        rolleId = bm.id!!,
                        overstyrGebyr = false,
                        begrunnelse = "Test",
                    ),
                )
            }
        exception.message shouldContain "Kan ikke sette begrunnelse hvis gebyr ikke er overstyrt"
    }

    @Test
    fun `skal oppdatere til defaultverdi fritatt ved avslag når ikke overstyr gebyr`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, null, true)
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterGebyrDto(
                rolleId = bm.id!!,
                overstyrGebyr = false,
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe null
    }

    @Test
    fun `skal sette gebyrvalg og begrunnelse ved avslag til innvilget`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(1000000000000))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        gebyrService.oppdaterManueltOverstyrtGebyr(
            behandling,
            OppdaterGebyrDto(
                rolleId = bm.id!!,
                overstyrGebyr = true,
                begrunnelse = "Begrunnelse",
            ),
        )

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal oppdatere gebyr til fritatt når det endres til avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe null
    }

    @Test
    fun `skal oppdatere gebyr når det endres til ikke avslag`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(false, true, "Begrunnelse", true)
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal oppdatere gebyr når det endres til ikke avslag hvis overstyrt`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(100))
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse", true)
        gebyrService.oppdaterGebyrEtterEndringÅrsakAvslag(behandling)

        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
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
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal rekalkulere men ikke oppdatere gebyr når beregnet verdi er samme`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(10000000))
        val bp = behandling.bidragspliktig
        bp!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse", true)
        val resultat = gebyrService.rekalkulerGebyr(behandling)

        resultat shouldBe false
        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe true
        bm.manueltOverstyrtGebyr!!.begrunnelse shouldBe "Begrunnelse"
    }

    @Test
    fun `skal rekalkulere og oppdatere gebyr når beregnet verdi er endret`() {
        val behandling = opprettBehandlingForGebyrberegning(BigDecimal(1000))
        val bp = behandling.bidragspliktig
        bp!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, "Begrunnelse", true)
        val resultat = gebyrService.rekalkulerGebyr(behandling)

        resultat shouldBe true
        bm.manueltOverstyrtGebyr.shouldNotBeNull()
        bm.manueltOverstyrtGebyr!!.overstyrGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.ilagtGebyr shouldBe false
        bm.manueltOverstyrtGebyr!!.beregnetIlagtGebyr shouldBe false
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
