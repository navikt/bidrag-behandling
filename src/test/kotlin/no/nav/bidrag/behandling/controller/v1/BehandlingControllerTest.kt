package no.nav.bidrag.behandling.controller.v1

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertNotNull

data class OpprettBehandlingRequestTest(
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype,
    val søktFomDato: LocalDate,
    val mottattdato: LocalDate,
    val søknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerenhet: String,
    val roller: Set<OpprettRolleDto>,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(101),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(456),
                ),
            )
        val behandlingReq = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(555),
                ),
            )
        val behandlingReq = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                HttpEntity(OppdaterBehandlingRequest(123L)),
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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(499),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(511),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(609),
                ),
            )
        val testBehandlingMedNull =
            oppretteBehandlingRequestTest(
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
            .opprettForsendelseKaltMed("\"gjelderIdent\":\"12345678911\"")
        stubUtils.Verify()
            .opprettForsendelseKaltMed("\"barnIBehandling\":[\"12345678910\"]")
    }

    @Test
    fun `skal opprette en behandling og ignorere feil hvis opprett forsendelse feiler`() {
        stubUtils.stubOpprettForsendelse(status = HttpStatus.BAD_REQUEST)
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(542),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
        var behandling =
            Behandling(
                vedtakstype = Vedtakstype.FASTSETTELSE,
                LocalDate.now().minusMonths(5),
                LocalDate.now().plusMonths(5),
                LocalDate.now(),
                saksnummer = "2400000",
                123213L,
                null,
                "EN12",
                "Z9999",
                "Navn Navnesen",
                "bisys",
                SøktAvType.VERGE,
                Stønadstype.FORSKUDD,
                null,
            )

        behandling.roller =
            mutableSetOf(
                Rolle(
                    ident = "12345678910",
                    behandling = behandling,
                    foedselsdato = LocalDate.now().minusMonths(133),
                    rolletype = Rolletype.BARN,
                ),
                Rolle(
                    ident = "12345678911",
                    behandling = behandling,
                    foedselsdato = LocalDate.now().minusMonths(333),
                    rolletype = Rolletype.BIDRAGSMOTTAKER,
                ),
            )

        val lagretBehandling =
            behandlingService.opprettBehandling(behandling)

        val vedtaksid: Long = 1
        val oppdaterBehandlingRequest =
            OppdaterBehandlingRequest(vedtaksid = vedtaksid)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${lagretBehandling.id}",
                HttpMethod.PUT,
                HttpEntity(oppdaterBehandlingRequest),
                BehandlingDto::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(vedtaksid, behandlingService.hentBehandlingById(lagretBehandling.id!!).vedtaksid)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull =
            oppretteBehandlingRequestTest(
                "1900000",
                "en12",
                setOf(
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
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
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", setOf())

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    null,
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(399),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    null,
                    fødselsdato = LocalDate.now().minusMonths(136),
                    navn = "Ola Dunk",
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(682),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12351234567"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    null,
                    navn = "Ola Dunk",
                    fødselsdato = LocalDate.now().minusMonths(529),
                ),
            )
        val testBehandlingMedNull = oppretteBehandlingRequestTest("1900000", "en12", roller)

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
    fun `skal ikke opprette en behandling med blank sak`() {
        val roller =
            setOf(
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(444),
                ),
            )
        val testBehandlingMedBlank = oppretteBehandlingRequestTest("   ", "en12", roller)
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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(478),
                ),
            )
        val testBehandlingMedBlank = oppretteBehandlingRequestTest("", "en12", roller)
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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(533),
                ),
            )
        val testBehandlingMedBlank = oppretteBehandlingRequestTest("123456789", "en12", roller)
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
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(500),
                ),
            )
        val b = oppretteBehandlingRequestTest(null, "1010", roller)
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
        fun oppretteBehandlingRequestTest(
            saksnummer: String?,
            enhet: String,
            roller: Set<OpprettRolleDto>,
        ): OpprettBehandlingRequestTest {
            return OpprettBehandlingRequestTest(
                Vedtakstype.FASTSETTELSE,
                Stønadstype.FORSKUDD,
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
