package no.nav.bidrag.behandling.controller

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.controller.v1.KontrollerTestRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
<<<<<<< HEAD
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
=======
import no.nav.bidrag.behandling.dto.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
>>>>>>> jsonb-merge
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.service.BehandlingServiceTest
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
<<<<<<< HEAD
=======
import no.nav.bidrag.domene.ident.Personident
>>>>>>> jsonb-merge
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
<<<<<<< HEAD
    val søknadstype: Soknadstype,
    val datoFom: LocalDate,
=======
    val søktFomDato: LocalDate,
>>>>>>> jsonb-merge
    val mottattdato: LocalDate,
    val søknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerenhet: String,
<<<<<<< HEAD
    val roller: Set<OppprettRolleDtoTest>,
=======
    val roller: Set<OpprettRolleDto>,
>>>>>>> jsonb-merge
)

data class OppprettRolleDtoTest(
    val rolletype: Rolletype,
    val ident: String?,
    val navn: String? = null,
    val fødselsdato: LocalDate,
    val opprettetDato: LocalDate? = null,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `skal opprette en behandling med null opprettetDato`() {
        val roller =
            setOf(
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1234",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678911"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(101),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(456),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1234",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(555),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(499),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(511),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(609),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(542),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
        val behandling =
            behandlingService.opprettBehandling(
                Behandling(
<<<<<<< HEAD
                    Vedtakstype.FASTSETTELSE,
=======
                    vedtakstype = Vedtakstype.FASTSETTELSE,
>>>>>>> jsonb-merge
                    LocalDate.now().minusMonths(5),
                    LocalDate.now().plusMonths(5),
                    LocalDate.now(),
                    Personident("12345678910").toString(),
                    123213L,
                    null,
                    "EN12",
                    "Z9999",
                    "Navn Navnesen",
                    "bisys",
                    SøktAvType.VERGE,
                    Stønadstype.FORSKUDD,
                    null,
                ),
            )

        val vedtaksid: Long = 1
        val oppdaterBehandlingRequest = OppdaterBehandlingRequest(vedtaksid = vedtaksid)

        val responseMedNull =
            httpHeaderTestRestTemplate.exchange(
<<<<<<< HEAD
                "${rootUriV1()}/behandling/${behandling.id}",
=======
                "${rootUri()}/behandling/${behandling.id}",
>>>>>>> jsonb-merge
                HttpMethod.PUT,
                HttpEntity(oppdaterBehandlingRequest),
                BehandlingDto::class.java,
            )
        assertEquals(HttpStatus.OK, responseMedNull.statusCode)
        assertEquals(vedtaksid, behandlingService.hentBehandlingById(behandling.id!!).vedtaksid)
    }

    @Test
    fun `skal ikke opprette en behandling med bare én rolle`() {
        val testBehandlingMedNull =
            oppretteBehandlingRequestTest(
                "1900000",
                "en12",
                setOf(
<<<<<<< HEAD
                    OppprettRolleDtoTest(
                        Rolletype.BARN,
                        "abc1s",
=======
                    OpprettRolleDto(
                        Rolletype.BARN,
                        Personident("12345678910"),
>>>>>>> jsonb-merge
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
=======
                OpprettRolleDto(
>>>>>>> jsonb-merge
                    Rolletype.BARN,
                    null,
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(399),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    null,
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    null,
                    fødselsdato = LocalDate.now().minusMonths(136),
>>>>>>> jsonb-merge
                    navn = "Ola Dunk",
                    fødselsdato = LocalDate.now().minusMonths(136),
                    LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(682),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "1235",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12351234567"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
=======
                OpprettRolleDto(
>>>>>>> jsonb-merge
                    Rolletype.BIDRAGSMOTTAKER,
                    null,
                    navn = "Ola Dunk",
                    fødselsdato = LocalDate.now().minusMonths(529),
                    LocalDate.now().minusMonths(8),
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

<<<<<<< HEAD
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
                "${rootUriV1()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                Void::class.java,
            )
        assertEquals(HttpStatus.BAD_REQUEST, responseMedNull.statusCode)
    }

=======
>>>>>>> jsonb-merge
    @Test
    fun `skal ikke opprette en behandling med blank sak`() {
        val roller =
            setOf(
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(444),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(478),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(533),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BARN,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(136),
                    opprettetDato = LocalDate.now().minusMonths(8),
                ),
<<<<<<< HEAD
                OppprettRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
=======
                OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678910"),
>>>>>>> jsonb-merge
                    fødselsdato = LocalDate.now().minusMonths(500),
                    opprettetDato = LocalDate.now().minusMonths(8),
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
<<<<<<< HEAD
            roller: Set<OppprettRolleDtoTest>,
=======
            roller: Set<OpprettRolleDto>,
>>>>>>> jsonb-merge
        ): OpprettBehandlingRequestTest {
            return OpprettBehandlingRequestTest(
                Vedtakstype.FASTSETTELSE,
                Stønadstype.FORSKUDD,
<<<<<<< HEAD
                Soknadstype.FASTSETTELSE,
=======
>>>>>>> jsonb-merge
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
