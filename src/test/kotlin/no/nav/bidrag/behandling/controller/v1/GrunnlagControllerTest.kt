package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.dto.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.grunnlag.GrunnlagDto
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

class GrunnlagControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal returnere 404 ved ugyldig behandling id`() {
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/1232132/grunnlag/${Grunnlagstype.BOFORHOLD.name}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                GrunnlagDto::class.java,
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
        val testBehandlingMedNull = BehandlingControllerTest.oppretteBehandlingRequestTest("1900000", "en12", roller)

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
                "${rootUri()}/behandling/${behandling.body!!.id}/grunnlag/${Grunnlagstype.BOFORHOLD_BEARBEIDET}/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                GrunnlagDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.NOT_FOUND, r.statusCode)
    }

    @Test
    fun `skal returnere 400 ved ugyldig type`() {
        val r =
            httpHeaderTestRestTemplate.exchange(
                "${rootUri()}/behandling/1232132/grunnlag/ERROR/aktiv",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                GrunnlagDto::class.java,
            )
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, r.statusCode)
    }
}
