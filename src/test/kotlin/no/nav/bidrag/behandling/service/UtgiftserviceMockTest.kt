package no.nav.bidrag.behandling.service

import io.kotest.assertions.throwables.shouldNotThrow
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
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.BeforeEach
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
    lateinit var notatService: NotatService

    @MockK
    lateinit var utgiftRepository: UtgiftRepository

    @MockK
    lateinit var tilgangskontrollService: TilgangskontrollService

    lateinit var utgiftService: UtgiftService
    lateinit var validering: ValiderBeregning
    lateinit var mapper: Dtomapper

    @BeforeEach
    fun initMock() {
        stubSjablonProvider()
        validering = ValiderBeregning()
        mapper = Dtomapper(tilgangskontrollService, validering)
        utgiftService = UtgiftService(behandlingRepository, notatService, utgiftRepository, mapper)
        every { utgiftRepository.save<Utgift>(any()) } answers {
            val utgift = firstArg<Utgift>()
            utgift.id = 1
            utgift.utgiftsposter.forEachIndexed { index, utgiftspost ->
                utgiftspost.id = utgiftspost.id ?: index.toLong()
            }
            utgift
        }
    }

    fun opprettBehandlingSærbidrag(): Behandling {
        val behandling = oppretteBehandling(1)
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.roller.addAll(oppretteBehandlingRoller(behandling, typeBehandling = TypeBehandling.SÆRBIDRAG))
        return behandling
    }

    @Test
    fun `skal kunne sette utgiftspost betalt av BP hvis engangsbeløptype ikke er av typen konfirmasjon `() {
        val behandling = opprettBehandlingSærbidrag()
        behandling.kategori = Særbidragskategori.OPTIKK.name
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
                    type = Utgiftstype.OPTIKK.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = 1,
                        dato = LocalDate.now().minusDays(1),
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Test",
                        betaltAvBp = true,
                    ),
            )
        shouldNotThrow<HttpClientErrorException> { utgiftService.oppdatereUtgift(behandling.id!!, forespørsel) }
    }

    @Test
    fun `skal ikke kunne sette utgiftspost type hvis kategori er av typen OPTIKK`() {
        val behandling = opprettBehandlingSærbidrag()
        behandling.kategori = Særbidragskategori.OPTIKK.name
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
                    type = Utgiftstype.OPTIKK.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = 1,
                        dato = LocalDate.now().minusDays(1),
                        type = Utgiftstype.OPTIKK.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Test",
                    ),
            )
        val exception =
            shouldThrow<HttpClientErrorException> { utgiftService.oppdatereUtgift(behandling.id!!, forespørsel) }

        exception.message shouldContain "Type kan ikke settes hvis behandling har kategori OPTIKK"
    }
}
