package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class UtgiftserviceTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var utgiftService: UtgiftService

    @BeforeEach
    fun initMock() {
    }

    fun opprettBehandlingSærligeUtgifter(): Behandling {
        val behandling = oppretteBehandling()
        behandling.engangsbeloptype = Engangsbeløptype.SÆRTILSKUDD
        return behandling
    }

    @Test
    @Transactional
    fun `skal opprette utgift og utgiftspost`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        testdataManager.lagreBehandlingNewTransaction(behandling)
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

    @Test
    @Transactional
    fun `skal oppdatere utgiftspost`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    beskrivelse = "Beskrivelse",
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostId = behandling.utgift!!.utgiftsposter.first().id
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = utgiftspostId,
                        dato = LocalDate.parse("2021-01-01"),
                        beskrivelse = "Beskrivelse ny",
                        kravbeløp = BigDecimal(2000),
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
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.parse("2021-01-01")
            beskrivelse shouldBe "Beskrivelse ny"
            kravbeløp shouldBe BigDecimal(2000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }
}
