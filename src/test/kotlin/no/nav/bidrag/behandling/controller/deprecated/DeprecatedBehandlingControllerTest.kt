package no.nav.bidrag.behandling.controller.deprecated

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.controller.v1.KontrollerTestRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.Ignore
import kotlin.test.assertNotNull

data class OpprettBehandlingRequestTest(
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype,
    val søknadstype: Soknadstype,
    val datoFom: LocalDate,
    val mottattdato: LocalDate,
    val søknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerenhet: String,
    val roller: Set<OppprettRolleDtoTest>,
)

data class OppprettRolleDtoTest(
    val rolletype: Rolletype,
    val ident: String?,
    val navn: String? = null,
    val fødselsdato: LocalDate,
    val opprettetDato: LocalDate? = null,
)

@Suppress("NonAsciiCharacters")
class DeprecatedBehandlingControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1234",
                    fødselsdato = LocalDate.now().minusMonths(101),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(456),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val behandlingReq = createBehandlingRequestTest("1900000", "en12", roller)

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(behandlingReq),
                OpprettBehandlingResponse::class.java,
            )
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)
        val opprettetBehandling = behandlingService.hentBehandlingById(behandlingRes.body!!.id)
        opprettetBehandling.opprettetAv shouldBe "aud-localhost"
        opprettetBehandling.opprettetAvNavn shouldBe "Fornavn Etternavn"
        opprettetBehandling.kildeapplikasjon shouldBe "aud-localhost"
    }

    @Test
    fun `skal opprette en behandling med null opprettetDato og så hente den`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1234",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(555),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val behandlingReq = createBehandlingRequestTest("1900000", "en12", roller)

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(behandlingReq),
                OpprettBehandlingResponse::class.java,
            )
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandlingRes.body!!.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                BehandlingDto::class.java,
            )

        assertNotNull(behandling.body)
        assertEquals(3, behandling.body!!.roller.size)
    }

    @Test
    fun `skal oppdatere behandling`() {
        val b = behandlingService.opprettBehandling(BehandlingServiceTest.prepareBehandling())

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/" + b.id,
                HttpMethod.PUT,
                HttpEntity(UpdateBehandlingRequest(123L)),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)

        val updatedBehandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${b!!.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                BehandlingDto::class.java,
            )

        assertNotNull(updatedBehandling.body)
        assertEquals(123L, updatedBehandling.body!!.grunnlagspakkeid)
    }

    @Test
    fun `skal opprette en behandling`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(499),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal opprette en behandling og ikke opprette forsendelse for forskudd`() {
        stubUtils.stubOpprettForsendelse()
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(511),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        stubUtils.Verify().opprettForsendelseIkkeKalt()
    }

    @Test
    fun `skal opprette en behandling og forsendelse for stonadType BIDRAG`() {
        stubUtils.stubOpprettForsendelse()
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(609),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull =
            createBehandlingRequestTest(
                "1900000",
                "en12",
                roller,
            ).copy(stønadstype = Stønadstype.BIDRAG)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"123\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"barnIBehandling\":[\"123\"]")
    }

    @Test
    fun `skal opprette en behandling og ignorere feil hvis opprett forsendelse feiler`() {
        stubUtils.stubOpprettForsendelse(status = HttpStatus.BAD_REQUEST)
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(542),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal opprette en behandling og oppdatere vedtak id`() {
        val behandling =
            behandlingService.opprettBehandling(
                Behandling(
                    Vedtakstype.FASTSETTELSE,
                    LocalDate.now().minusMonths(5),
                    LocalDate.now().plusMonths(5),
                    LocalDate.now(),
                    "123",
                    123213L,
                    null,
                    "EN123",
                    "Z9999",
                    "Navn Navnesen",
                    "bisys",
                    SøktAvType.VERGE,
                    Stønadstype.FORSKUDD,
                    null,
                ),
            )

        val vedtaksid: Long = 1
        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/vedtak/$vedtaksid",
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(vedtaksid, behandlingService.hentBehandlingById(behandling.id!!).vedtaksid)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull =
            createBehandlingRequestTest(
                "1900000",
                "en12",
                setOf(
                    OppprettRolleDtoTest(
                        Rolletype.BARN,
                        "abc1s",
                        fødselsdato = LocalDate.now().minusMonths(136),
                        opprettetDato = LocalDate.now().minusMonths(8),
                    ),
                ),
            )

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling uten roller`() {
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", setOf())

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette behandling som inkluderer barn uten navn og ident`() {
        // given
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    null,
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(399),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        // when
        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )

        // then
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal opprette behandling som inkluderer barn med navn men uten ident`() { // given
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    null,
                    navn = "Ola Dunk",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(682),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        // when
        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )

        // then
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette behandling som inkluderer BP uten ident`() {
        // given
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1235",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    null,
                    navn = "Ola Dunk",
                    fødselsdato = LocalDate.now().minusMonths(529),
                    LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        // when
        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )

        // then
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Ignore
    @Test
    fun `skal ikke opprette en behandling med rolle med blank ident`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "   ",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(555),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("1900000", "en12", roller)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(444),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedBlank = createBehandlingRequestTest("   ", "en12", roller)
        val responseMedBlank =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedBlank),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak1`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(478),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedBlank = createBehandlingRequestTest("", "en12", roller)
        val responseMedBlank =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedBlank),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med lang sak`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(533),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val testBehandlingMedBlank = createBehandlingRequestTest("123456789", "en12", roller)
        val responseMedBlank =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedBlank),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med ugyldig enhet`() {
        val roller =
            setOf(
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(500),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
            )
        val b = createBehandlingRequestTest(null, "12312312312", roller)
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(b),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, r.statusCode)
    }

    companion object {
        fun createBehandlingRequestTest(
            saksnummer: String?,
            enhet: String,
            roller: Set<OppprettRolleDtoTest>,
        ): OpprettBehandlingRequestTest {
            return OpprettBehandlingRequestTest(
                Vedtakstype.FASTSETTELSE,
                Stønadstype.FORSKUDD,
                Soknadstype.FASTSETTELSE,
                LocalDate.now().minusMonths(4),
                LocalDate.now(),
                SøktAvType.BIDRAGSMOTTAKER,
                saksnummer,
                enhet,
                roller,
            )
        }
    }
}
