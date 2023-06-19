package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntekterResponse
import no.nav.bidrag.behandling.dto.inntekt.UtvidetbarnetrygdDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date
import kotlin.test.assertEquals

data class TestInntektRequest(
    val inntekter: Set<TestInntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetbarnetrygdDto>,
)

data class TestInntektDto(
    val id: Long?,
    val taMed: Boolean,
    val beskrivelse: String?,
    val belop: String,
    val datoTom: String?,
    val datoFom: String?,
    val ident: String,
)

class InntekterControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal opprette og oppdatere inntekter`() {
        val roller = setOf(
            CreateRolleDtoTest(RolleType.BARN, "123", Date(1)),
            CreateRolleDtoTest(RolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
        )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling",
            HttpMethod.POST,
            HttpEntity(testBehandlingMedNull),
            CreateBehandlingResponse::class.java,
        )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        val inn = TestInntektDto(null, true, "some", "1.123", "2022-10-10", "2022-10-10", "blablabla")

        // 2. Add inntekter
        val r = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/$behandlingId/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(setOf(inn), emptySet(), emptySet())),
            InntekterResponse::class.java,
        )

        Assertions.assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(1, r.body!!.inntekter.size)

        // 3. Add some more inntekter
        val inntekt2 = inn.copy(id = r.body!!.inntekter.iterator().next().id, datoFom = null, beskrivelse = null)

        val r1 = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/$behandlingId/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(setOf(inn, inntekt2), setOf(), setOf())),
            InntekterResponse::class.java,
        )

        Assertions.assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(2, r1.body!!.inntekter.size)

        // 4. Remove inntekter
        val r2 = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/$behandlingId/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(emptySet(), emptySet(), emptySet())),
            InntekterResponse::class.java,
        )

        Assertions.assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(0, r2.body!!.inntekter.size)
    }
}
