package no.nav.bidrag.behandling.controller

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import stubPersonConsumer
import java.math.BigDecimal
import java.math.MathContext

class BehandlingBeregnControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var samværRepository: SamværRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @MockkBean
    lateinit var forskuddBeregning: BeregnForskuddApi

    val responseType = object : ParameterizedTypeReference<List<ResultatBeregningBarnDto>>() {}

    @BeforeEach
    fun oppsett() {
        samværRepository.deleteAll()
        behandlingRepository.deleteAll()
        every { forskuddBeregning.beregn(any()) } returns BeregnetForskuddResultat()
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
        stubUtils.stubHentePersonInfoForTestpersoner()
        stubUtils.stubAlleBidragVedtakForStønad()
        stubUtils.stubBidraBBMHentBeregning()
        stubUtils.stubBidragBeløpshistorikkLøpendeSaker()
    }

    @Test
    fun `skal beregne forskudd for validert behandling`() {
        // given
        val behandling = lagreBehandling(opprettGyldigBehandlingForBeregningOgVedtak())

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn/forskudd",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                responseType,
            )

        // then
        assertSoftly(returnert) {
            this shouldNotBe null
            statusCode shouldBe HttpStatus.OK
            body?.shouldHaveSize(2)
            assertSoftly(body!!.find { it.barn.ident!!.verdi == testdataBarn1.ident }!!) {
                barn.ident!!.verdi shouldBe testdataBarn1.ident
                barn.navn shouldBe testdataBarn1.navn
                barn.fødselsdato shouldBe testdataBarn1.fødselsdato
                perioder shouldHaveSize 6
                assertSoftly(perioder[0]) {
                    periode shouldBe ÅrMånedsperiode("2023-02", "2023-07")
                    beløp shouldBe BigDecimal(1760)
                    inntekt shouldBe BigDecimal("120000.00")
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                    resultatKode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
                    regel shouldBe "REGEL 6"
                    antallBarnIHusstanden shouldBe 3
                }
            }
            assertSoftly(body!!.find { it.barn.ident!!.verdi == testdataBarn2.ident }!!) {
                barn.ident!!.verdi shouldBe testdataBarn2.ident
                barn.navn shouldBe testdataBarn2.navn
                barn.fødselsdato shouldBe testdataBarn2.fødselsdato
                perioder shouldHaveSize 6
                assertSoftly(perioder[0]) {
                    periode shouldBe ÅrMånedsperiode("2023-02", "2023-07")
                    beløp shouldBe BigDecimal(1760)
                    inntekt shouldBe BigDecimal("60000.00")
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                    resultatKode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
                    regel shouldBe "REGEL 6"
                    antallBarnIHusstanden shouldBe 3
                }
            }
        }
    }

    @Test
    fun `skal beregne særbidrag for validert behandling`() {
        // given
        val behandling =
            lagreBehandling(
                opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG).let { behandling ->
                    behandling.inntekter.add(
                        Inntekt(
                            belop = BigDecimal(6000000),
                            datoFom = behandling.virkningstidspunkt,
                            datoTom = null,
                            ident = behandling.bidragspliktig!!.ident!!,
                            taMed = true,
                            kilde = Kilde.MANUELL,
                            behandling = behandling,
                            type = Inntektsrapportering.KAPITALINNTEKT,
                            id = null,
                        ),
                    )
                    behandling
                },
            )

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn/sarbidrag",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ResultatSærbidragsberegningDto::class.java,
            )

        // then
        assertSoftly(returnert) {
            this shouldNotBe null
            statusCode shouldBe HttpStatus.OK
        }

        assertSoftly(returnert.body!!) {
            it.resultat shouldBe BigDecimal("2083")
            it.resultatKode shouldBe Resultatkode.SÆRBIDRAG_INNVILGET
            it.voksenIHusstanden shouldBe true
            it.bpHarEvne shouldBe true
            it.delberegningUtgift!!.sumGodkjent shouldBe BigDecimal(2500)
            it.beregning!!.totalKravbeløp shouldBe BigDecimal(3000)
            it.bpsAndel!!.andelBeløp shouldBe BigDecimal("2083.33")
            it.bpsAndel.endeligAndelFaktor.round(MathContext(3)) shouldBe BigDecimal(0.8333).round(MathContext(3))
        }
    }

    @Test
    fun `skal returnere httpkode 400 dersom behandling mangler informasjon om husstandsmedlem`() {
        // given
        val behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                BeregningValideringsfeil::class.java,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldNotBe null
            returnert.body!!.virkningstidspunkt shouldBe null
            returnert.body!!.husstandsmedlem shouldBe null
            returnert.body!!.sivilstand shouldNotBe null
            assertSoftly(returnert.body!!.sivilstand!!) {
                hullIPerioder shouldHaveSize 0
                overlappendePerioder shouldHaveSize 0
                fremtidigPeriode shouldBe false
                manglerPerioder shouldBe true
                ingenLøpendePeriode shouldBe false
            }
            assertSoftly(returnert.body!!.inntekter!!) {
                barnetillegg shouldBe null
                utvidetBarnetrygd shouldBe null
                kontantstøtte shouldBe null
                småbarnstillegg shouldBe null
                årsinntekter shouldNotBe null
                årsinntekter!! shouldHaveSize 1
            }
        }
    }

    fun lagreBehandling(behandling: Behandling): Behandling {
        val lagretBehandling = behandlingRepository.save(behandling)
        val grunnlag =
            opprettAlleAktiveGrunnlagFraFil(
                behandling,
                "grunnlagresponse.json",
            )
        grunnlagRepository.saveAll(grunnlag)
        return lagretBehandling
    }

    private fun lagreBehandlingMedRoller(): Behandling {
        val behandling = oppretteBehandling()
        behandling.roller = oppretteBehandlingRoller(behandling)
        return behandlingRepository.save(behandling)
    }
}
