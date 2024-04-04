package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereSivilstand
import no.nav.bidrag.behandling.dto.v2.boforhold.PersonaliaHusstandsbarn
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
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

        // 2.2
        val boforholdData =
            OppdatereBoforholdRequestV2(
                oppdatereHusstandsbarn =
                    OppdatereHusstandsbarn(
                        PersonaliaHusstandsbarn(
                            navn = "Per Spelemann",
                            fødselsdato = LocalDate.now().minusMonths(687),
                        ),
                    ),
                oppdatereSivilstand =
                    OppdatereSivilstand(
                        leggeTilSivilstandsperiode =
                            Sivilstandsperiode(
                                fraOgMed = LocalDate.now().minusYears(5),
                                tilOgMed = null,
                                sivilstand = Sivilstandskode.ENSLIG,
                            ),
                    ),
                oppdatereNotat =
                    OppdaterNotat(
                        "med i vedtak",
                        "kun i notat",
                    ),
            ) //
        val boforholdResponse =
            httpHeaderTestRestTemplate.exchange(
                "${rootUriV2()}/behandling/${behandling.id}",
                HttpMethod.PUT,
                HttpEntity(OppdaterBehandlingRequestV2(boforhold = boforholdData)),
                BehandlingDtoV2::class.java,
            )

        assertEquals(1, boforholdResponse.body!!.boforhold.husstandsbarn.size)
        val husstandsBarnDto = boforholdResponse.body!!.boforhold.husstandsbarn.iterator().next()
        assertEquals(1, husstandsBarnDto.perioder.size)
    }
}
