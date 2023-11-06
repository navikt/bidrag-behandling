package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.CreateRolleRolleType
import no.nav.bidrag.behandling.dto.behandling.UpdateBehandlingRequest
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.SøktAvType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.*
import kotlin.test.Ignore
import kotlin.test.assertNotNull

data class CreateBehandlingRequestTest(
    val behandlingType: BehandlingType,
    val stonadType: Stønadstype,
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val mottatDato: Date,
    val soknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerEnhet: String,
    val roller: Set<CreateRolleDtoTest>,
)

data class CreateRolleDtoTest(
    val rolleType: CreateRolleRolleType,
    val ident: String?,
    val opprettetDato: Date?,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest() : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "1234", null),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val behandlingReq = createBehandlingRequestTest("sak123", "en12", roller)

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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "1234", null),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val behandlingReq = createBehandlingRequestTest("sak123", "en12", roller)

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
        assertEquals(123L, updatedBehandling.body!!.grunnlagspakkeId)
    }

    @Test
    fun `skal opprette en behandling`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull =
            createBehandlingRequestTest(
                "sak123",
                "en12",
                roller,
            ).copy(stonadType = Stønadstype.BIDRAG)

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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

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
                    BehandlingType.FORSKUDD,
                    SoknadType.FASTSETTELSE,
                    Date(1),
                    Date(2),
                    Date(1),
                    "123",
                    123213L,
                    null,
                    "EN123",
                    SøktAvType.VERGE,
                    null,
                    null,
                ),
            )

        val VEDTAK_ID: Long = 1
        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.id}/vedtak/$VEDTAK_ID",
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(VEDTAK_ID, behandlingService.hentBehandlingById(behandling.id!!).vedtakId)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull =
            createBehandlingRequestTest(
                "sak123",
                "en12",
                setOf(CreateRolleDtoTest(CreateRolleRolleType.BARN, "abc1s", Date(1))),
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
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", setOf())

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
    fun `skal ikke opprette en behandling med rolle med null ident`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, null, Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Ignore
    @Test
    fun `skal ikke opprette en behandling med rolle med blank ident`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "   ", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
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
                    BehandlingType.FORSKUDD,
                    Stønadstype.FORSKUDD,
                    SoknadType.FASTSETTELSE,
                    Date(1),
                    Date(1),
                    Date(1),
                    SøktAvType.BIDRAGSMOTTAKER,
                    saksnummer,
                    enhet,
                    roller,
                )
            return testBehandling
        }
    }
}
