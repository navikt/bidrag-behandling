package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
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
                no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto(
                    Rolletype.BARN,
                    Personident("12345678910"),
                    fødselsdato = LocalDate.now().minusMonths(136),
                ),
                no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto(
                    Rolletype.BIDRAGSMOTTAKER,
                    Personident("12345678911"),
                    fødselsdato = LocalDate.now().minusMonths(529),
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
                no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse::class.java,
            )
        Assertions.assertEquals(HttpStatus.OK, behandling.statusCode)

        // 2.1 Prepare husstandsBarn
        val perioder =
            setOf(
                no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto(
                    null,
                    null,
                    null,
                    Bostatuskode.IKKE_MED_FORELDER,
                    Kilde.OFFENTLIG,
                ),
            )
        val husstandsBarn =
            setOf(
                no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto(
                    behandling.body!!.id,
                    true,
                    perioder,
                    "ident",
                    null,
                    fødselsdato = LocalDate.now().minusMonths(687),
                ),
            )

        // 2.2
        val boforholdData =
            no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest(
                husstandsBarn,
                emptySet(),
                notat =
                no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat(
                    "med i vedtak",
                    "kun i notat",
                ),
            ) //
        val boforholdResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/${behandling.body!!.id}",
                HttpMethod.PUT,
                HttpEntity(no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest(boforhold = boforholdData)),
                no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto::class.java,
            )

        assertEquals(1, boforholdResponse.body!!.boforhold.husstandsbarn.size)
        val husstandsBarnDto = boforholdResponse.body!!.boforhold.husstandsbarn.iterator().next()
        assertEquals("ident", husstandsBarnDto.ident)
        assertEquals(1, husstandsBarnDto.perioder.size)
    }
}
