package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
<<<<<<< HEAD
import no.nav.bidrag.domene.enums.rolle.Rolletype
=======
>>>>>>> main
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.Ignore
import kotlin.test.assertNotNull

data class CreateBehandlingRequestTest(
    val behandlingstype: Behandlingstype,
    val stønadstype: Stønadstype,
    val søknadstype: Soknadstype,
    val datoFom: LocalDate,
    val datoTom: LocalDate,
    val mottattdato: LocalDate,
    val søknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerenhet: String,
    val roller: Set<CreateRolleDtoTest>,
)

data class CreateRolleDtoTest(
    val rolletype: Rolletype,
    val ident: String?,
    val opprettetDato: LocalDate?,
    val navn: String? = null,
    val fødselsdato: LocalDate,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller =
            setOf(
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "1234",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(101),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(456),
                ),
            )
        val behandlingReq = createBehandlingRequestTest("1900000", "en12", roller)

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(behandlingReq),
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)
    }

    @Test
    fun `skal opprette en behandling med null opprettetDato og så hente den`() {
        val roller =
            setOf(
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(Rolletype.BARN, "1234", opprettetDato = null, fødselsdato = LocalDate.now().minusMonths(136)),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(555),
                ),
            )
        val behandlingReq = createBehandlingRequestTest("1900000", "en12", roller)

        val behandlingRes =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(behandlingReq),
                CreateBehandlingResponse::class.java,
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
        val b = behandlingService.createBehandling(BehandlingServiceTest.prepareBehandling())

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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(499),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(511),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(609),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(542),
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
            behandlingService.createBehandling(
                Behandling(
                    Behandlingstype.FORSKUDD,
                    Soknadstype.FASTSETTELSE,
                    LocalDate.now().minusMonths(5),
                    LocalDate.now().plusMonths(5),
                    LocalDate.now(),
                    "123",
                    123213L,
                    null,
                    "EN123",
                    SøktAvType.VERGE,
                    null,
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
                    CreateRolleDtoTest(
                        Rolletype.BARN,
                        "abc1s",
                        opprettetDato = LocalDate.now().minusMonths(8),
                        fødselsdato = LocalDate.now().minusMonths(136),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    null,
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(399),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    null,
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                    navn = "Ola Dunk",
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(682),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "1235",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    null,
                    navn = "Ola Dunk",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(529),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "   ",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(555),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(444),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(478),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(533),
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
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(500),
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
            roller: Set<CreateRolleDtoTest>,
        ): CreateBehandlingRequestTest {
            val testBehandling =
                CreateBehandlingRequestTest(
                    Behandlingstype.FORSKUDD,
                    Stønadstype.FORSKUDD,
                    Soknadstype.FASTSETTELSE,
                    LocalDate.now().minusMonths(4),
                    LocalDate.now().plusMonths(4),
                    LocalDate.now(),
                    SøktAvType.BIDRAGSMOTTAKER,
                    saksnummer,
                    enhet,
                    roller,
                )
            return testBehandling
        }
    }
}
