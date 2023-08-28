package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.domain.enums.StonadType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date
import kotlin.test.Ignore

data class CreateBehandlingRequestTest(
    val behandlingType: BehandlingType,
    val stonadType: StonadType,
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
class BehandlingControllerTest() : KontrollerTestRunner() {

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BARN, "1234", null),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val behandlingReq = createBehandlingRequestTest("sak123", "en12", roller)

        val behandlingRes = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(behandlingReq),
            Void::class.java
        )
        assertEquals(HttpStatus.OK, behandlingRes.statusCode)
    }

    @Test
    fun `skal opprette en behandling`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal opprette en behandling og ikke opprette forsendelse for forskudd`() {
        stubUtils.stubOpprettForsendelse()
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        stubUtils.Verify().opprettForsendelseIkkeKalt()
    }

    @Test
    fun `skal opprette en behandling og forsendelse for stonadType BIDRAG`() {
        stubUtils.stubOpprettForsendelse()
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest(
            "sak123",
            "en12",
            roller
        ).copy(stonadType = StonadType.BIDRAG)

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
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
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
    }

    @Test
    fun `skal opprette en behandling og oppdatere vedtak id`() {
        val behandling = behandlingService.createBehandling(
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
                SoknadFraType.VERGE,
                null,
                null,
            ),
        )

        val VEDTAK_ID: Long = 1
        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/${behandling.id}/vedtak/$VEDTAK_ID",
            HttpMethod.PUT,
            HttpEntity.EMPTY,
            Void::class.java
        )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(VEDTAK_ID, behandlingService.hentBehandlingById(behandling.id!!).vedtakId)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull = createBehandlingRequestTest(
            "sak123",
            "en12",
            setOf(CreateRolleDtoTest(RolleType.BARN, "abc1s", Date(1)))
        )

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling uten roller`() {
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", setOf())

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med rolle med null ident`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, null, Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
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

        val responseMedNull = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1))
        )
        val testBehandlingMedBlank = createBehandlingRequestTest("   ", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedBlank),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med blank sak1`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1))
        )
        val testBehandlingMedBlank = createBehandlingRequestTest("", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedBlank),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med lang sak`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1))
        )
        val testBehandlingMedBlank = createBehandlingRequestTest("123456789", "en12", roller)
        val responseMedBlank = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedBlank),
            Void::class.java
        )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedBlank.statusCode)
    }

    @Test
    fun `skal ikke opprette en behandling med ugyldig enhet`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1))
        )
        val b = createBehandlingRequestTest(null, "12312312312", roller)
        val r = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(b),
            Void::class.java
        )
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
                StonadType.FORSKUDD,
                SoknadType.FASTSETTELSE,
                Date(1),
                Date(1),
                Date(1),
                SoknadFraType.BIDRAGSMOTTAKER,
                saksnummer,
                enhet,
                roller,
            )
            return testBehandling
        }
    }
}
