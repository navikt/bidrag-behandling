package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.boforhold.dto.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import java.time.LocalDate
import kotlin.test.assertEquals

class BoforholdControllerTest : KontrollerTestRunner() {
    @Test
    fun `skal lagre boforhold data`() {
        // 1. Create new behandling
        val behandling = testdataManager.opprettBehandling()

        // 2.1 Prepare husstandsBarn
        val perioder =
            setOf(
                HusstandsbarnperiodeDto(
                    null,
                    LocalDate.parse("2022-01-01"),
                    null,
                    Bostatuskode.IKKE_MED_FORELDER,
                    Kilde.OFFENTLIG,
                ),
            )

        val husstandsBarn =
            setOf(
                HusstandsbarnDtoV2(
                    behandling.id,
                    Kilde.OFFENTLIG,
                    true,
                    perioder,
                    "ident",
                    null,
                    fødselsdato = LocalDate.now().minusMonths(687),
                ),
            )

        // 2.2
        val boforholddata =
            OppdaterBoforholdRequest(
                husstandsBarn,
                emptySet(),
                notat =
                    OppdaterNotat(
                        "med i vedtak",
                        "kun i notat",
                    ),
            )
        val boforholdResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}",
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequestV2(boforhold = boforholddata)),
                BehandlingDtoV2::class.java,
            )

        assertEquals(1, boforholdResponse.body!!.boforhold.husstandsbarn.size)
        val husstandsBarnDto = boforholdResponse.body!!.boforhold.husstandsbarn.iterator().next()
        assertEquals(1, husstandsBarnDto.perioder.size)
    }
}
