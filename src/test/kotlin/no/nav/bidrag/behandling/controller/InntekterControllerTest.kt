package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntekterResponse
import no.nav.bidrag.behandling.dto.inntekt.UtvidetbarnetrygdDto
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class TestInntektRequest(
    val inntekter: Set<TestInntektDto>,
    val barnetillegg: Set<BarnetilleggDto>,
    val utvidetbarnetrygd: Set<UtvidetbarnetrygdDto>,
)

data class TestInntektDto(
    val id: Long?,
    val taMed: Boolean,
    val inntektType: String?,
    val belop: String,
    val datoTom: String?,
    val datoFom: String?,
    val ident: String,
    val inntektPostListe: Set<InntektPost>,
)

class InntekterControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Test
    fun `skal opprette og oppdatere inntekter`() {
        // 1. Create new behandling
        val behandling: Behandling = behandlingRepository.save(
            Behandling(
                BehandlingType.FORSKUDD,
                SoknadType.FASTSETTELSE,
                Date(1),
                Date(1),
                Date(1),
                "123",
                123,
                null,
                "ENH",
                SoknadFraType.BIDRAGSMOTTAKER,
                null,
                null,
            ),
        )

        val inn = TestInntektDto(null, true, "some0", "1.123", "2022-10-10", "2022-10-10", "blablabla", setOf(InntektPost("ABC", "ABC", BigDecimal.TEN)))
//        val inn1 = TestInntektDto(null, true, "some1", "1.123", "2022-10-10", "2022-10-10", "blablabla", setOf(InntektPost("ABC1", "ABC1", BigDecimal.TEN), InntektPost("ABC2", "ABC2", BigDecimal.TEN)))

        // 2. Add inntekter
        val r = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/${behandling.id}/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(setOf(inn), emptySet(), emptySet())),
            InntekterResponse::class.java,
        )

        assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(1, r.body!!.inntekter.size)

        // 3. Add some more inntekter
        val inntekt1 = inn.copy(id = r.body!!.inntekter.iterator().next().id, inntektPostListe = setOf(InntektPost("ABC1", "ABC1", BigDecimal.TEN), InntektPost("ABC2", "ABC2", BigDecimal.TEN)))
        val inntekt2 = inn.copy(datoFom = null, inntektType = "null")

        val r1 = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/${behandling.id}/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(setOf(inntekt1, inntekt2), setOf(), setOf())),
            InntekterResponse::class.java,
        )

        assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(2, r1.body!!.inntekter.size)
        assertNotNull(r1.body!!.inntekter.find { it.inntektType == "some0" && it.inntektPostListe.size == 2 })
        assertNotNull(r1.body!!.inntekter.find { it.inntektType == "null" && it.inntektPostListe.size == 1 })

        // 4. Remove inntekter
        val r2 = httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/behandling/${behandling.id}/inntekter",
            HttpMethod.PUT,
            HttpEntity(TestInntektRequest(emptySet(), emptySet(), emptySet())),
            InntekterResponse::class.java,
        )

        assertEquals(HttpStatus.OK, r.statusCode)
        assertEquals(0, r2.body!!.inntekter.size)
    }
}
