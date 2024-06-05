package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.UtgiftRepository
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockKExtension::class)
class UtgiftserviceMockTest {
    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var utgiftRepository: UtgiftRepository

    lateinit var utgiftService: UtgiftService

    @BeforeEach
    fun initMock() {
        utgiftService = UtgiftService(behandlingRepository, utgiftRepository)
        every { utgiftRepository.save<Utgift>(any()) } answers {
            val utgift = firstArg<Utgift>()
            utgift.id = 1
            utgift.utgiftsposter.forEachIndexed { index, utgiftspost ->
                utgiftspost.id = utgiftspost.id ?: index.toLong()
            }
            utgift
        }
    }

    fun opprettBehandlingSærligeUtgifter(): Behandling {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRTILSKUDD
        return behandling
    }

    @Test
    fun `skal opprette og oppdatere utgift`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.parse("2021-01-01"),
                        beskrivelse = "Beskrivelse",
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(1, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        response.totalBeløpBetaltAvBp shouldBe BigDecimal(0)
        response.beregnetBeløp shouldBe BigDecimal(500)
        response.beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.parse("2021-01-01")
            beskrivelse shouldBe "Beskrivelse"
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }
}
