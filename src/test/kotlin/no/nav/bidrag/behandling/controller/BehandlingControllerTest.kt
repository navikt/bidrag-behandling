package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.AvslagType
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.BehandlingBarnDto
import no.nav.bidrag.behandling.dto.BehandlingDto
import no.nav.bidrag.behandling.dto.CreateBehandlingResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date

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
    val opprettetDato: Date,
)

data class UpdateBehandlingRequestTest(
    val avslag: String? = null,
    val aarsak: String? = null,
    val virkningsDato: String? = null,
    val behandlingBarn: Set<BehandlingBarnDto>? = null,
)

data class UpdateBehandlingRequestNonExistingFieldTest(
    val begrunnelseMedIVedtakNotat: String? = null,
    val avslag: String? = null,
)

class BehandlingControllerTest : KontrollerTestRunner() {

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
    fun `skal opprette og oppdatere en behandling`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = createBehandlingRequestTest("sak123", "en12", roller)

        val responseMedNull = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(testBehandlingMedNull), CreateBehandlingResponse::class.java)
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)

        val behandlingBarn = setOf(BehandlingBarnDto(null, true, Calendar.getInstance().time, Calendar.getInstance().time, BoStatusType.BARN_BOR_ALENE, "Manuelt", "ident!"))

        val updateReq = UpdateBehandlingRequestTest(
            avslag = AvslagType.MANGL_DOK.name,
            virkningsDato = "01.02.2023",
            behandlingBarn = behandlingBarn,
        )
        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/${responseMedNull.body!!.id}", HttpMethod.PUT, HttpEntity(updateReq), BehandlingDto::class.java)

        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(AvslagType.MANGL_DOK, updatedBehandling.body!!.avslag)

        val expectedDate = LocalDate.parse("01.02.2023", DateTimeFormatter.ofPattern("dd.MM.uuuu"))
        assertEquals(expectedDate, updatedBehandling.body!!.virkningsDato)
    }

    @Test
    fun `skal validere datoer`() {
        val updateReq = UpdateBehandlingRequestTest(avslag = AvslagType.MANGL_DOK.name, virkningsDato = "1.2.2023")
        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
    }

    @Test
    fun `skal ignorere felt som ikke eksisterer i backend`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val createBehandling = createBehandlingRequestTest("sak123", "en12", roller)

        val behandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling", HttpMethod.POST, HttpEntity(createBehandling), CreateBehandlingResponse::class.java)
        assertEquals(HttpStatus.OK, behandling.statusCode)

        val updateReq = UpdateBehandlingRequestNonExistingFieldTest(avslag = AvslagType.MANGL_DOK.name, begrunnelseMedIVedtakNotat = "Some text")
        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/${behandling.body!!.id}", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)

        assertEquals(HttpStatus.OK, updatedBehandling.statusCode)
    }

    @Test
    fun `skal validere avlag type not blank`() {
        val updateReq = UpdateBehandlingRequestTest(avslag = " ")
        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
    }

    @Test
    fun `skal validere aarsak type not blank`() {
        val updateReq = UpdateBehandlingRequestTest(aarsak = "")
        val updatedBehandling = httpHeaderTestRestTemplate.exchange("${rootUri()}/behandling/123", HttpMethod.PUT, HttpEntity(updateReq), Void::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, updatedBehandling.statusCode)
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

    // @Test
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

    private fun createBehandlingRequestTest(saksnummer: String?, enhet: String, roller: Set<CreateRolleDtoTest>): CreateBehandlingRequestTest {
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
