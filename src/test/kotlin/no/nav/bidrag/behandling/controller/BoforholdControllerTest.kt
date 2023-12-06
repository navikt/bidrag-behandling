package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.behandling.CreateBehandlingResponse
import no.nav.bidrag.behandling.dto.boforhold.BoforholdResponse
import no.nav.bidrag.behandling.dto.boforhold.UpdateBoforholdRequest
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnPeriodeDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals

class BoforholdControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal lagre boforhold data`() {
        val roller =
            setOf(
                CreateRolleDtoTest(
                    Rolletype.BARN,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                CreateRolleDtoTest(
                    Rolletype.BIDRAGSMOTTAKER,
                    "123",
                    opprettetDato = LocalDate.now().minusMonths(8),
                    fødselsdato = LocalDate.now().minusMonths(529),
                ),
            )
        val testBehandlingMedNull = BehandlingControllerTest.createBehandlingRequestTest("1900000", "en12", roller)

        // 1. Create new behandling
        val behandling =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling",
                HttpMethod.POST,
                HttpEntity(testBehandlingMedNull),
                CreateBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        // 2.1 Prepare husstandsBarn

        val perioder =
            setOf(HusstandsBarnPeriodeDto(null, null, null, Bostatuskode.IKKE_MED_FORELDER, Kilde.OFFENTLIG))
        val husstandsBarn =
            setOf(
                HusstandsbarnDto(
                    behandling.body!!.id,
                    true,
                    perioder,
                    "ident",
                    null,
                    fødselsdato = LocalDate.now().minusMonths(687),
                ),
            )

        // 2.2
        val boforholdData = UpdateBoforholdRequest(husstandsBarn, emptySet(), "med i vedtak", "kun i notat") //
        val boforholdResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.body!!.id}/boforhold",
                HttpMethod.PUT,
                HttpEntity(boforholdData),
                BoforholdResponse::class.java,
            )

        assertEquals(1, boforholdResponse.body!!.husstandsBarn.size)
        val husstandsBarnDto = boforholdResponse.body!!.husstandsBarn.iterator().next()
        assertEquals("ident", husstandsBarnDto.ident)
        assertEquals(1, husstandsBarnDto.perioder.size)
    }
}
