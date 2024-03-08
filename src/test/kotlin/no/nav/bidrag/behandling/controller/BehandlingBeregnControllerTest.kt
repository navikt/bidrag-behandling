package no.nav.bidrag.behandling.controller

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.utils.testdata.opprettAlleAktiveGrunnlagFraFil
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import stubPersonConsumer
import java.math.BigDecimal

class BehandlingBeregnControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    @MockkBean
    lateinit var forskuddBeregning: BeregnForskuddApi

    val responseType = object : ParameterizedTypeReference<List<ResultatBeregningBarnDto>>() {}

    @BeforeEach
    fun oppsett() {
        behandlingRepository.deleteAll()
        every { forskuddBeregning.beregn(any()) } returns BeregnetForskuddResultat()
        stubSjablonProvider()
        stubKodeverkProvider()
        stubPersonConsumer()
    }

    @Test
    fun `skal beregne forskudd for validert behandling`() {
        // given
        val behandling = lagreBehandling(opprettGyldigBehandlingForBeregningOgVedtak())

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn",
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
                barn.fødselsdato shouldBe testdataBarn1.foedselsdato
                perioder shouldHaveSize 4
                assertSoftly(perioder[0]) {
                    periode shouldBe ÅrMånedsperiode("2023-02", "2023-07")
                    beløp shouldBe BigDecimal(1760)
                    inntekt shouldBe BigDecimal(120000)
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                    resultatKode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
                    regel shouldBe "REGEL 6"
                    antallBarnIHusstanden shouldBe 2
                }
            }
            assertSoftly(body!!.find { it.barn.ident!!.verdi == testdataBarn2.ident }!!) {
                barn.ident!!.verdi shouldBe testdataBarn2.ident
                barn.navn shouldBe testdataBarn2.navn
                barn.fødselsdato shouldBe testdataBarn2.foedselsdato
                perioder shouldHaveSize 4
                assertSoftly(perioder[0]) {
                    periode shouldBe ÅrMånedsperiode("2023-02", "2023-07")
                    beløp shouldBe BigDecimal(1760)
                    inntekt shouldBe BigDecimal(60000)
                    sivilstand shouldBe Sivilstandskode.BOR_ALENE_MED_BARN
                    resultatKode shouldBe Resultatkode.FORHØYET_FORSKUDD_100_PROSENT
                    regel shouldBe "REGEL 6"
                    antallBarnIHusstanden shouldBe 2
                }
            }
        }
    }

    @Test
    fun `skal returnere httpkode 400 dersom behandling mangler informasjon om husstandsbarn`() {
        // given
        val behandling = lagreBehandlingMedRoller()

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                responseType,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.shouldBe(
                listOf(
                    "Sivilstand mangler i behandling",
                    "Mangler inntekter for bidragsmottaker",
                    "Mangler informasjon om husstandsbarn",
                ),
            )
        }
    }

    @Test
    @Disabled("")
    fun `skal videreføre feil fra bidrag-beregn-forskudd-rest`() {
        // given
        val errorMessage = "Feil input"
        every { forskuddBeregning.beregn(any()) } throws IllegalArgumentException(errorMessage)
        val behandling = lagreBehandling(opprettGyldigBehandlingForBeregningOgVedtak())

        // when
        val returnert =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV1()}/behandling/${behandling.id}/beregn",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                responseType,
            )

        // then
        assertSoftly {
            returnert shouldNotBe null
            returnert.statusCode shouldBe HttpStatus.BAD_REQUEST
            returnert.body shouldBe null
            returnert.headers["Warning"]?.shouldBe(
                listOf(
                    errorMessage,
                    errorMessage,
                ),
            )
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