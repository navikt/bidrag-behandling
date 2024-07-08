package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
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
        behandling.engangsbeloptype = Engangsbeløptype.SÆRBIDRAG
        behandling.kategori = Særbidragskategori.KONFIRMASJON.name
        return behandling
    }

    @Test
    @Transactional
    fun `skal sette avslag`() {
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                avslag = Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        response.utgiftposter shouldHaveSize 0
        response.avslag shouldBe Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
    }

    @Test
    @Transactional
    fun `skal fjerne avslag`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.avslag = Resultatkode.PRIVAT_AVTALE_OM_SÆRBIDRAG
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                avslag = null,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        response.utgiftposter shouldHaveSize 1
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
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.KLÆR,
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        response.avslag shouldBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(500)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.KLÆR
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost for kategori OPTIKK`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.OPTIKK.name
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(500)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.OPTIKK
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost for kategori TANNREGULERING`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.kategori = Særbidragskategori.TANNREGULERING.name
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.TANNREGULERING,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(1000)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.TANNREGULERING
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostId =
            behandling.utgift!!
                .utgiftsposter
                .first()
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = utgiftspostId,
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.KONFIRMASJONSAVGIFT,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(500)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløpBp shouldBe BigDecimal(0)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT
            kravbeløp shouldBe BigDecimal(2000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost med beløp betalt av BP`() {
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.REISEUTGIFT,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                        betaltAvBp = true,
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null

        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(500)
            totalGodkjentBeløp shouldBe BigDecimal(1000)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløpBp shouldBe BigDecimal(500)
        }
        response.utgiftposter shouldHaveSize 2
        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.REISEUTGIFT
            kravbeløp shouldBe BigDecimal(2000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere beløp direkte betalt av BP`() {
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                beløpDirekteBetaltAvBp = BigDecimal(1500),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.oppdatertUtgiftspost shouldBe null

        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(2000)
            totalGodkjentBeløp shouldBe BigDecimal(1000)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1500)
            totalGodkjentBeløpBp shouldBe BigDecimal(500)
        }
        response.utgiftposter shouldHaveSize 2
    }

    @Test
    @Transactional
    fun `skal slette utgiftspost`() {
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.parse("2022-01-01"),
                    type = Utgiftstype.REISEUTGIFT,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostSlettId =
            behandling.utgift!!
                .utgiftsposter
                .find { it.dato == LocalDate.parse("2022-01-01") }!!
                .id
        val utgiftspostId =
            behandling.utgift!!
                .utgiftsposter
                .find { it.dato == LocalDate.parse("2021-01-01") }!!
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                sletteUtgift = utgiftspostSlettId,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 1
        assertSoftly(response.utgiftposter[0]) {
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.parse("2021-01-01")
        }

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 1
    }

    @Test
    @Transactional
    fun `skal angre sletting av utgiftspost`() {
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
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.parse("2022-01-01"),
                    type = Utgiftstype.REISEUTGIFT,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostSlettId =
            behandling.utgift!!
                .utgiftsposter
                .find { it.dato == LocalDate.parse("2022-01-01") }!!
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                sletteUtgift = utgiftspostSlettId,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 1

        val forespørselAngre =
            OppdatereUtgiftRequest(
                angreSisteEndring = true,
            )
        val responseAngre = utgiftService.oppdatereUtgift(behandling.id!!, forespørselAngre)
        responseAngre.utgiftposter shouldHaveSize 2

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 2
    }

    @Test
    @Transactional
    fun `skal angre endring på utgiftspost`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(2),
                    type = Utgiftstype.KONFIRMASJONSLEIR,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    begrunnelse = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostId =
            behandling.utgift!!
                .utgiftsposter
                .first()
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        id = utgiftspostId,
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.REISEUTGIFT,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        begrunnelse = "Test",
                    ),
            )
        val responseOppdater = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        assertSoftly(responseOppdater.utgiftposter[0]) {
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.REISEUTGIFT
            kravbeløp shouldBe BigDecimal(2000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
        val forespørselAngre =
            OppdatereUtgiftRequest(
                angreSisteEndring = true,
            )
        val responseAngre = utgiftService.oppdatereUtgift(behandling.id!!, forespørselAngre)
        assertSoftly(responseAngre.utgiftposter[0]) {
            dato shouldBe LocalDate.now().minusMonths(2)
            type shouldBe Utgiftstype.KONFIRMASJONSLEIR
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            begrunnelse shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere notat`() {
        val behandling = opprettBehandlingSærligeUtgifter()
        val forespørsel =
            OppdatereUtgiftRequest(
                notat = OppdaterNotat("Ny notat"),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        response.notat.kunINotat shouldBe "Ny notat"

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)!!
        behandlingEtter.utgiftsbegrunnelseKunINotat shouldBe "Ny notat"
    }
}
