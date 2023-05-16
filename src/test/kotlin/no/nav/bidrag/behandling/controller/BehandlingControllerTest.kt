package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date
import kotlin.test.Ignore

data class CreateBehandlingRequestTest(
    val behandlingType: BehandlingType,
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val mottatDato: Date,
    val soknadFra: SoknadFraType,
    val saksnummer: String?,
    val behandlerEnhet: String,
    val roller: Set<CreateRolleDtoTest>,
)

data class CreateRolleDtoTest(
    val rolleType: RolleType,
    val ident: String?,
    val opprettetDato: Date?,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest : KontrollerTestRunner() {

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BARN, "1234", null),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val behandlingReq = createBehandlingRequestTest("sak123", "en12", roller)

        val behandlingRes = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(behandlingReq), Void::class.java)
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)
    }

    @Test
    fun `skal opprette en behandling`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), Void::class.java)
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", setOf(CreateRolleDtoTest(RolleType.BARN, "abc1s", Date(1))))

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling uten roller`() {
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", setOf())

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med rolle med null ident`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, null, Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Ignore
    @Test
    fun `skal ikke opprette en behandling med rolle med blank ident`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "   ", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak`() {
        val roller = setOf(CreateRolleDtoTest(RolleType.BARN, "123", Date(1)), CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)))
        val testBehandlingMedBlank = createBehandlingRequestTest("   ", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedBlank), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak1`() {
        val roller = setOf(CreateRolleDtoTest(RolleType.BARN, "123", Date(1)), CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)))
        val testBehandlingMedBlank = createBehandlingRequestTest("", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedBlank), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med lang sak`() {
        val roller = setOf(CreateRolleDtoTest(RolleType.BARN, "123", Date(1)), CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)))
        val testBehandlingMedBlank = createBehandlingRequestTest("123456789", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedBlank), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med ugyldig enhet`() {
        val roller = setOf(CreateRolleDtoTest(RolleType.BARN, "123", Date(1)), CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)))
        val b = createBehandlingRequestTest(null, "12312312312", roller)
        val r = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(b), Void::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, r.statusCode)
    }

    companion object {
        fun createBehandlingRequestTest(
            saksnummer: String?,
            enhet: String,
            roller: Set<CreateRolleDtoTest>,
        ): CreateBehandlingRequestTest {
            val testBehandling = CreateBehandlingRequestTest(
                BehandlingType.FORSKUDD,
                SoknadType.SOKNAD,
                Date(1),
                Date(1),
                Date(1),
                SoknadFraType.BM,
                saksnummer,
                enhet,
                roller,
            )
            return testBehandling
        }
    }
}
