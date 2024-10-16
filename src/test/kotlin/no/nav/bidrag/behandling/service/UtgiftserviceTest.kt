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
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.behandling.dto.v2.utgift.MaksGodkjentBeløpDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftRequest
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

class UtgiftserviceTest : TestContainerRunner() {
    @Autowired
    lateinit var testdataManager: TestdataManager

    @Autowired
    lateinit var utgiftService: UtgiftService

    @BeforeEach
    fun initMock() {
        stubSjablonProvider()
        stubKodeverkProvider()
    }

    fun oppretteBehandlingForSærbidrag(): Behandling = oppretteBehandling(false, false, true, true, TypeBehandling.SÆRBIDRAG, true)

    @Test
    @Transactional
    fun `skal sette avslag`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                avslag = Resultatkode.PRIVAT_AVTALE,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        response.utgiftposter shouldHaveSize 0
        response.avslag shouldBe Resultatkode.PRIVAT_AVTALE
    }

    @Test
    @Transactional
    fun `skal fjerne avslag`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.avslag = Resultatkode.PRIVAT_AVTALE
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2021-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
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
    fun `skal skal sortere utgiftsposter i responsen etter oppdatering`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(5),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Eldste post",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusDays(5),
                    type = Utgiftstype.REISEUTGIFT.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Nyeste post",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.KLÆR.name,
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Ny post",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        assertSoftly(response.utgiftposter) {
            shouldHaveSize(3)
            this[0].kommentar shouldBe "Eldste post"
            this[1].kommentar shouldBe "Ny post"
            this[2].kommentar shouldBe "Nyeste post"
        }

        val forespørsel2 =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusDays(1),
                        type = Utgiftstype.KLÆR.name,
                        id = response.oppdatertUtgiftspost?.id,
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Ny post",
                    ),
            )
        val response2 = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel2)
        assertSoftly(response2.utgiftposter) {
            shouldHaveSize(3)
            this[0].kommentar shouldBe "Eldste post"
            this[1].kommentar shouldBe "Nyeste post"
            this[2].kommentar shouldBe "Ny post"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgift og utgiftspost`() {
        val behandling = oppretteBehandlingForSærbidrag()
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.KLÆR.name,
                        kravbeløp = BigDecimal(3000),
                        godkjentBeløp = BigDecimal(3000),
                        kommentar = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        response.avslag shouldBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(3000)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.KLÆR.name
            kravbeløp shouldBe BigDecimal(3000)
            godkjentBeløp shouldBe BigDecimal(3000)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost for kategori OPTIKK`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.kategori = Særbidragskategori.OPTIKK.name
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Test",
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
            type shouldBe Utgiftstype.OPTIKK.name
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost for kategori TANNREGULERING`() {
        val behandling = oppretteBehandlingForSærbidrag()
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
                    type = Utgiftstype.TANNREGULERING.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
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
                        kommentar = "Test",
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
            type shouldBe Utgiftstype.TANNREGULERING.name
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost for kategori ANNET`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.kategori = Særbidragskategori.ANNET.name
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusDays(3),
                    type = "Kvittering for medisiner",
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        type = "Kvittering for medisiner 2",
                        kravbeløp = BigDecimal(1000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Test",
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
            type shouldBe "Kvittering for medisiner 2"
            kravbeløp shouldBe BigDecimal(1000)
            godkjentBeløp shouldBe BigDecimal(500)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere utgiftspost`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
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
                        type = Utgiftstype.KONFIRMASJONSAVGIFT.name,
                        kravbeløp = BigDecimal(3000),
                        godkjentBeløp = BigDecimal(2000),
                        kommentar = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null
        response.avslag shouldBe null
        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløp shouldBe BigDecimal(2000)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløpBp shouldBe BigDecimal(0)
            totalKravbeløp shouldBe BigDecimal(3000)
        }

        assertSoftly(response.oppdatertUtgiftspost!!) {
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.KONFIRMASJONSAVGIFT.name
            kravbeløp shouldBe BigDecimal(3000)
            godkjentBeløp shouldBe BigDecimal(2000)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal opprette utgiftspost med beløp betalt av BP`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(3000),
                    godkjentBeløp = BigDecimal(3000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(1),
                        type = Utgiftstype.REISEUTGIFT.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(500),
                        kommentar = "Test",
                        betaltAvBp = true,
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response shouldNotBe null
        response.oppdatertUtgiftspost shouldNotBe null

        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(500)
            totalGodkjentBeløp shouldBe BigDecimal(3500)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(0)
            totalGodkjentBeløpBp shouldBe BigDecimal(500)
            totalKravbeløp shouldBe BigDecimal(5000)
        }
        response.avslag shouldBe null
        response.utgiftposter shouldHaveSize 2
        assertSoftly(response.oppdatertUtgiftspost!!) {
            dato shouldBe LocalDate.now().minusMonths(1)
            type shouldBe Utgiftstype.REISEUTGIFT.name
            kravbeløp shouldBe BigDecimal(2000)
            godkjentBeløp shouldBe BigDecimal(500)
            kommentar shouldBe "Test"
        }
    }

    @Test
    @Transactional
    fun `skal oppdatere beløp direkte betalt av BP`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
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
        response.avslag shouldBe null

        assertSoftly(response.beregning!!) {
            totalBeløpBetaltAvBp shouldBe BigDecimal(2000)
            totalGodkjentBeløp shouldBe BigDecimal(2500)
            beløpDirekteBetaltAvBp shouldBe BigDecimal(1500)
            totalGodkjentBeløpBp shouldBe BigDecimal(500)
            totalKravbeløp shouldBe BigDecimal(3000)
        }
        response.utgiftposter shouldHaveSize 2
    }

    @Test
    @Transactional
    fun `skal ta med maks godkjent beløp`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                maksGodkjentBeløp =
                    MaksGodkjentBeløpDto(
                        taMed = true,
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.oppdatertUtgiftspost shouldBe null
        response.avslag shouldBe null

        assertSoftly(response.maksGodkjentBeløp!!) {
            taMed shouldBe true
            beløp shouldBe null
            begrunnelse shouldBe null
        }
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.maksGodkjentBeløp shouldBe null
        behandlingEtter!!.utgift!!.maksGodkjentBeløpBegrunnelse shouldBe null
        behandlingEtter!!.utgift!!.maksGodkjentBeløpTaMed shouldBe true
    }

    @Test
    @Transactional
    fun `skal oppdatere maks godkjent beløp`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                maksGodkjentBeløp =
                    MaksGodkjentBeløpDto(
                        beløp = BigDecimal(6000),
                        begrunnelse = "Kommentar maks godkjent",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.oppdatertUtgiftspost shouldBe null
        response.avslag shouldBe null

        assertSoftly(response.maksGodkjentBeløp!!) {
            taMed shouldBe true
            beløp shouldBe BigDecimal(6000)
            begrunnelse shouldBe "Kommentar maks godkjent"
        }
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.maksGodkjentBeløp shouldBe BigDecimal(6000)
        behandlingEtter!!.utgift!!.maksGodkjentBeløpBegrunnelse shouldBe "Kommentar maks godkjent"
        behandlingEtter!!.utgift!!.maksGodkjentBeløpTaMed shouldBe true
    }

    @Test
    @Transactional
    fun `skal slette maks godkjent beløp`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
            )
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(100)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Dette er kommentar"
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                maksGodkjentBeløp =
                    MaksGodkjentBeløpDto(
                        taMed = false,
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.oppdatertUtgiftspost shouldBe null
        response.avslag shouldBe null

        assertSoftly(response.maksGodkjentBeløp!!) {
            taMed shouldBe false
            beløp shouldBe BigDecimal(100)
            begrunnelse shouldBe "Dette er kommentar"
        }
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.maksGodkjentBeløp shouldBe BigDecimal(100)
        behandlingEtter!!.utgift!!.maksGodkjentBeløpBegrunnelse shouldBe "Dette er kommentar"
        behandlingEtter!!.utgift!!.maksGodkjentBeløpTaMed shouldBe false
    }

    @Test
    @Transactional
    fun `skal ikke oppdatere maks godkjent beløp hvis ikke satt i forespørsel`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
            )
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(6000)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "Dette er kommentar"
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(2000),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(500),
                    kommentar = "Test",
                    betaltAvBp = true,
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                maksGodkjentBeløp = null,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.oppdatertUtgiftspost shouldBe null
        response.avslag shouldBe null

        assertSoftly(response.maksGodkjentBeløp!!) {
            beløp shouldBe BigDecimal(6000)
            begrunnelse shouldBe "Dette er kommentar"
        }
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.maksGodkjentBeløp shouldBe BigDecimal(6000)
        behandlingEtter!!.utgift!!.maksGodkjentBeløpBegrunnelse shouldBe "Dette er kommentar"
    }

    @Test
    @Transactional
    fun `skal slette maks godkjent beløp hvis ingen utgiftsposter`() {
        val behandling = oppretteBehandlingForSærbidrag()

        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.maksGodkjentBeløpTaMed = true
        behandling.utgift!!.maksGodkjentBeløp = BigDecimal(100)
        behandling.utgift!!.maksGodkjentBeløpBegrunnelse = "DEtte er test"
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(2000),
                    godkjentBeløp = BigDecimal(1600),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostSlettId =
            behandling.utgift!!
                .utgiftsposter
                .first()
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                sletteUtgift = utgiftspostSlettId,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 0
        response.maksGodkjentBeløp!!.beløp shouldBe null
        response.maksGodkjentBeløp!!.taMed shouldBe false
        response.maksGodkjentBeløp!!.begrunnelse shouldBe null

        response.avslag shouldBe null

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 0
        behandlingEtter!!.utgift!!.maksGodkjentBeløp shouldBe null
        behandlingEtter!!.utgift!!.maksGodkjentBeløpBegrunnelse shouldBe null
        behandlingEtter!!.utgift!!.maksGodkjentBeløpTaMed shouldBe false
    }

    @Test
    @Transactional
    fun `skal slette utgiftspost`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(3),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(5000),
                    godkjentBeløp = BigDecimal(3600),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
                Utgiftspost(
                    dato = LocalDate.now().minusMonths(4),
                    type = Utgiftstype.REISEUTGIFT.name,
                    kravbeløp = BigDecimal(5000),
                    godkjentBeløp = BigDecimal(3500),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val utgiftspostSlettId =
            behandling.utgift!!
                .utgiftsposter
                .find { it.dato == LocalDate.now().minusMonths(3) }!!
                .id
        val utgiftspostId =
            behandling.utgift!!
                .utgiftsposter
                .find { it.dato == LocalDate.now().minusMonths(4) }!!
                .id
        val forespørsel =
            OppdatereUtgiftRequest(
                sletteUtgift = utgiftspostSlettId,
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 1
        assertSoftly(response.utgiftposter[0]) {
            id shouldBe utgiftspostId
            dato shouldBe LocalDate.now().minusMonths(4)
        }
        response.avslag shouldBe null

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 1
    }

    @Test
    @Transactional
    fun `skal oppdatere notat`() {
        val behandling = oppretteBehandlingForSærbidrag()
        val forespørsel =
            OppdatereUtgiftRequest(
                oppdatereBegrunnelse = OppdatereBegrunnelse("Nytt notat"),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)
        response.begrunnelse shouldBe "Nytt notat"
        response.avslag shouldBe null

        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)!!
        behandlingEtter.notater.first { Notattype.UTGIFTER == it.type }.innhold shouldBe "Nytt notat"
    }

    @Test
    @Transactional
    fun `skal returnere avslag hvis alle utgifsposter er foreldet`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2020-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(0),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusYears(3),
                        type = Utgiftstype.REISEUTGIFT.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(0),
                        kommentar = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 2
        assertSoftly(response.utgiftposter[1]) {
            dato shouldBe LocalDate.now().minusYears(3)
        }

        response.avslag shouldBe Resultatkode.ALLE_UTGIFTER_ER_FORELDET
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 2
    }

    @Test
    @Transactional
    fun `skal returnere avslag hvis alle godkjent beløp er lavere enn forskuddsats`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2020-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(0),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(3),
                        type = Utgiftstype.REISEUTGIFT.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(100),
                        kommentar = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 2
        assertSoftly(response.utgiftposter[1]) {
            dato shouldBe LocalDate.now().minusMonths(3)
        }

        response.avslag shouldBe Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 2
    }

    @Test
    @Transactional
    fun `skal ikke returnere avslag hvis alle godkjent beløp er lavere enn forskuddsats hvis endring`() {
        val behandling = oppretteBehandlingForSærbidrag()
        behandling.vedtakstype = Vedtakstype.ENDRING
        behandling.utgift =
            Utgift(
                behandling = behandling,
                beløpDirekteBetaltAvBp = BigDecimal(0),
            )
        behandling.utgift!!.utgiftsposter =
            mutableSetOf(
                Utgiftspost(
                    dato = LocalDate.parse("2020-01-01"),
                    type = Utgiftstype.KONFIRMASJONSLEIR.name,
                    kravbeløp = BigDecimal(1000),
                    godkjentBeløp = BigDecimal(0),
                    kommentar = "Test",
                    utgift = behandling.utgift!!,
                ),
            )
        testdataManager.lagreBehandlingNewTransaction(behandling)
        val forespørsel =
            OppdatereUtgiftRequest(
                nyEllerEndretUtgift =
                    OppdatereUtgift(
                        dato = LocalDate.now().minusMonths(3),
                        type = Utgiftstype.REISEUTGIFT.name,
                        kravbeløp = BigDecimal(2000),
                        godkjentBeløp = BigDecimal(100),
                        kommentar = "Test",
                    ),
            )
        val response = utgiftService.oppdatereUtgift(behandling.id!!, forespørsel)

        response.utgiftposter shouldHaveSize 2
        assertSoftly(response.utgiftposter[1]) {
            dato shouldBe LocalDate.now().minusMonths(3)
        }

        response.avslag shouldBe null
        val behandlingEtter = testdataManager.hentBehandling(behandling.id!!)
        behandlingEtter!!.utgift!!.utgiftsposter shouldHaveSize 2
    }
}
