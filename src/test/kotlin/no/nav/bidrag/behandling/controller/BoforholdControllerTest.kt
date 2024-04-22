package no.nav.bidrag.behandling.controller

import jakarta.persistence.EntityManager
import junit.framework.TestCase.assertFalse
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.Husstandsbarnperiode
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdResponse
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import org.junit.experimental.runners.Enclosed
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(Enclosed::class)
class BoforholdControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var entityManager: EntityManager

    @Nested
    open inner class OppdatereBehandling {
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

    @Nested
    open inner class OppdatereBoforhold {
        // TODO: Ferdigstille etter np er fikset i BoforholdApi.beregn2
        @Test
        @Disabled(
            "Skrudd av i påvente av fiks i beregnV2. Får nullpointer dersom to " +
                "bostatus-innslag med null tom-dato kommer etter hverandre",
        )
        fun `skal kunne legge til åpen manuell periode like etter en åpen offentlig periode`() {
            // gitt
            val behandling = testdataManager.opprettBehandling()

            val eksisterendeHusstandsbarn = behandling.husstandsbarn.find { it.ident == testdataBarn1.ident }
            val sistePeriode = eksisterendeHusstandsbarn!!.perioder.maxBy { it.datoFom!! }

            val request =
                OppdatereBoforholdRequestV2(
                    oppdatereHusstandsbarn =
                        OppdatereHusstandsbarn(
                            nyHusstandsbarnperiode =
                                Husstandsbarnperiode(
                                    idHusstandsbarn = eksisterendeHusstandsbarn.id!!,
                                    bostatus = Bostatuskode.MED_FORELDER,
                                    fraOgMed = sistePeriode.datoFom!!.plusMonths(2),
                                    tilOgMed = null,
                                ),
                        ),
                )

            // hvis
            val boforholdResponse =
                httpHeaderTestRestTemplate.exchange(
                    "${rootUriV2()}/behandling/${behandling.id}/boforhold",
                    HttpMethod.PUT,
                    HttpEntity(request),
                    OppdatereBoforholdResponse::class.java,
                )

            // så
            assertFalse(true)
        }
    }
}
