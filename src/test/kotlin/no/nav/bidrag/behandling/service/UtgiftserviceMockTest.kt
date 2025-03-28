package no.nav.bidrag.behandling.service

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkClass
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.UtgiftRepository
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.transformers.Dtomapper
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandlingRoller
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
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

    @MockK
    lateinit var vedtakGrunnlagMapper: VedtakGrunnlagMapper

    @MockK
    lateinit var behandlingService: BehandlingService

    @MockK
    lateinit var personService: PersonService

    lateinit var utgiftService: UtgiftService
    lateinit var validering: ValiderBeregning
    val validerBehandling: ValiderBehandlingService = mockkClass(ValiderBehandlingService::class)
    lateinit var mapper: Dtomapper

    @BeforeEach
    fun initMock() {
        stubSjablonProvider()
        validering = ValiderBeregning()
        val beregnBarnebidragApi = BeregnBarnebidragApi()
        mapper =
            Dtomapper(
                tilgangskontrollService,
                validering,
                validerBehandling,
                vedtakGrunnlagMapper,
                beregnBarnebidragApi,
            )
        utgiftService = UtgiftService(behandlingRepository, notatService, utgiftRepository, mapper)
        every { validerBehandling.kanBehandlesINyLøsning(any()) } returns null
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
        behandling.roller.addAll(oppretteBehandlingRoller(behandling, typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true))
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
