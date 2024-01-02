package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.deprecated.dto.OpplysningerDto
import no.nav.bidrag.behandling.deprecated.modell.OpplysningerType
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

class OpplysningerControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal opprette og oppdatere opplysninger`() {
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
                    fødselsdato = LocalDate.now().minusMonths(568),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", false, OpplysningerType.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp1\"}", true, OpplysningerType.BOFORHOLD_BEARBEIDET)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/BOFORHOLD/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult1.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult1.body!!.behandlingId)
        Assertions.assertEquals("{\"test\": \"opp1\"}", oppAktivResult1.body!!.data)
    }

    @Test
    fun `skal ikke være mulig å opprette flere aktive opplysninger`() {
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
                    fødselsdato = LocalDate.now().minusMonths(429),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", true, OpplysningerType.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp2\"}", true, OpplysningerType.BOFORHOLD_BEARBEIDET)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/BOFORHOLD/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult1.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult1.body!!.behandlingId)
        Assertions.assertEquals("{\"test\": \"opp2\"}", oppAktivResult1.body!!.data)
    }

    @Test
    fun `skal returnere 404 ved ugyldig behandling id`() {
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/1232132/opplysninger/${OpplysningerType.BOFORHOLD_BEARBEIDET.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.NOT_FOUND, r.statusCode)
    }

    @Test
    fun `skal returnere 404 hvis opplysninger ikke eksisterer for en gitt behandling`() {
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
                    fødselsdato = LocalDate.now().minusMonths(471),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        // 2. Check
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.body!!.id}/opplysninger/${OpplysningerType.BOFORHOLD_BEARBEIDET.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.NOT_FOUND, r.statusCode)
    }

    @Test
    fun `skal opprette og oppdatere opplysninger1`() {
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
                    fødselsdato = LocalDate.now().minusMonths(409),
                ),
            )
        val testBehandlingMedNull =
            BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp\"}", false, OpplysningerType.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"opp1\"}", false, OpplysningerType.BOFORHOLD_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"inn1\"}", false, OpplysningerType.INNTEKT_BEARBEIDET)
        skalOppretteOpplysninger(behandlingId, "{\"test\": \"inn2\"}", false, OpplysningerType.INNTEKT_BEARBEIDET)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/${OpplysningerType.BOFORHOLD_BEARBEIDET.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult1.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult1.body!!.behandlingId)
        Assertions.assertEquals("{\"test\": \"opp1\"}", oppAktivResult1.body!!.data)

        // 4. Assert that inn1 is active
        val oppAktivResult2 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/${OpplysningerType.INNTEKT_BEARBEIDET.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult2.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult2.body!!.behandlingId)
        Assertions.assertEquals("{\"test\": \"inn2\"}", oppAktivResult2.body!!.data)
    }

    data class AddOpplysningerRequest(
        val behandlingId: Long,
        val aktiv: Boolean,
        val opplysningerType: OpplysningerType,
        val data: String,
        val hentetDato: String,
    )

    private fun skalOppretteOpplysninger(
        behandlingId: Long,
        data: String,
        aktiv: Boolean,
        opplysningerType: OpplysningerType,
    ): OpplysningerDto {
        val opplysninger = createOpplysninger(behandlingId, data, aktiv, opplysningerType)

        val opp =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger",
                HttpMethod.POST,
                HttpEntity(opplysninger),
                OpplysningerDto::class.java,
            )

        Assertions.assertEquals(HttpStatus.OK, opp.statusCode)
        val body = opp.body!!
        Assertions.assertEquals(behandlingId, body.behandlingId)

        return body
    }

    private fun createOpplysninger(
        behandlingId: Long,
        data: String,
        aktiv: Boolean,
        opplysningerType: OpplysningerType,
    ): AddOpplysningerRequest {
        val opplysninger =
            AddOpplysningerRequest(behandlingId, aktiv, opplysningerType, data, "2025-02-01")
        return opplysninger
    }
}
