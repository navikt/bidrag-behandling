package no.nav.bidrag.behandling.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.UtgiftRepository
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.client.HttpClientErrorException
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
        val behandling = oppretteBehandling(1)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRTILSKUDD_KONFIRMASJON
        return behandling
    }

    @Test
    fun `skal ikke kunne sette utgiftspost betalt av BP hvis engangsbeløptype ikke er av typen konfirmasjon `() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRTILSKUDD_OPTIKK
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    id = 1,
                    dato = LocalDate.parse("2021-01-01"),
                    beskrivelse = "Beskrivelse",
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = 1,
                        dato = LocalDate.parse("2021-01-01"),
                        beskrivelse = "Beskrivelse",
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                        betaltAvBp = true,
                    ),
            )
        val exception =
            shouldThrow<HttpClientErrorException> { utgiftService.oppdatereUtgift(behandling.id!!, forespørsel) }

        exception.message shouldContain "Kan ikke legge til utgift betalt av BP for særlige utgifter behandling som ikke har kategori konfirmasjon"
    }

    @Nested
    inner class OppdaterUtgiftRequestValideringTest
}