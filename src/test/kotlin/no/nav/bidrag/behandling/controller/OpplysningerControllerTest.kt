package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.CreateRolleRolleType
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Date
import kotlin.test.Ignore

data class AddOpplysningerRequest(
    val behandlingId: Long,
    val aktiv: Boolean,
    val opplysningerType: OpplysningerType,
    val data: String,
    val hentetDato: String,
)

class OpplysningerControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal opprette og oppdatere opplysninger`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                CreateBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "opp", false, OpplysningerType.BOFORHOLD)
        skalOppretteOpplysninger(behandlingId, "opp1", true, OpplysningerType.BOFORHOLD)

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
        Assertions.assertEquals("opp1", oppAktivResult1.body!!.data)
    }

    @Test
    fun `skal ikke være mulig å opprette flere aktive opplysninger`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                CreateBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "opp", true, OpplysningerType.BOFORHOLD)
        skalOppretteOpplysninger(behandlingId, "opp1", true, OpplysningerType.BOFORHOLD)

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
        Assertions.assertEquals("opp1", oppAktivResult1.body!!.data)
    }

    @Test
    fun `skal returnere 404 ved ugyldig behandling id`() {
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/1232132/opplysninger/${OpplysningerType.BOFORHOLD.name}/aktiv",
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
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                CreateBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        // 2. Check
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.body!!.id}/opplysninger/${OpplysningerType.BOFORHOLD.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.NOT_FOUND, r.statusCode)
    }

    @Test
    @Ignore // Må fikses i validerings logikken
    fun `skal returnere 400 ved ugyldig type`() {
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/1232132/opplysninger/ERROR/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, r.statusCode)
    }

    @Test
    fun `skal opprette og oppdatere opplysninger1`() {
        val roller =
            setOf(
                CreateRolleDtoTest(CreateRolleRolleType.BARN, "123", Date(1)),
                CreateRolleDtoTest(CreateRolleRolleType.BIDRAGS_MOTTAKER, "123", Date(1)),
            )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("sak123", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                CreateBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        val behandlingId = behandling.body!!.id

        // 2. Create new opplysninger opp and opp1
        skalOppretteOpplysninger(behandlingId, "opp", false, OpplysningerType.BOFORHOLD)
        skalOppretteOpplysninger(behandlingId, "opp1", false, OpplysningerType.BOFORHOLD)
        skalOppretteOpplysninger(behandlingId, "inn0", false, OpplysningerType.INNTEKTSOPPLYSNINGER)
        skalOppretteOpplysninger(behandlingId, "inn1", false, OpplysningerType.INNTEKTSOPPLYSNINGER)

        // 3. Assert that opp1 is active
        val oppAktivResult1 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/${OpplysningerType.BOFORHOLD.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult1.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult1.body!!.behandlingId)
        Assertions.assertEquals("opp1", oppAktivResult1.body!!.data)

        // 4. Assert that inn1 is active
        val oppAktivResult2 =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/$behandlingId/opplysninger/${OpplysningerType.INNTEKTSOPPLYSNINGER.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                OpplysningerDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, oppAktivResult2.statusCode)
        Assertions.assertEquals(behandlingId, oppAktivResult2.body!!.behandlingId)
        Assertions.assertEquals("inn1", oppAktivResult2.body!!.data)
    }

    private fun skalOppretteOpplysninger(behandlingId: Long, data: String, aktiv: Boolean, opplysningerType: OpplysningerType): OpplysningerDto {
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

    private fun createOpplysninger(behandlingId: Long, data: String, aktiv: Boolean, opplysningerType: OpplysningerType): AddOpplysningerRequest {
        val opplysninger =
            AddOpplysningerRequest(behandlingId, aktiv, opplysningerType, data, "2025-02-01")
        return opplysninger
    }
}
