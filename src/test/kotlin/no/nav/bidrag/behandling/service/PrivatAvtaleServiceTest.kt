package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.dto.v2.behandling.DatoperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleRequest
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.transformers.validerePrivatAvtale
import no.nav.bidrag.behandling.utils.testdata.leggTilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.utils.testdata.leggTilNotat
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.opprettPrivatAvtale
import no.nav.bidrag.behandling.utils.testdata.opprettPrivatAvtalePeriode
import no.nav.bidrag.behandling.utils.testdata.opprettStønadPeriodeDto
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import stubPersonRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@ExtendWith(MockKExtension::class)
class PrivatAvtaleServiceTest {
    @MockK
    lateinit var behandlingService: BehandlingService

    var notatService: NotatService = NotatService()
    lateinit var personRepository: PersonRepository
    lateinit var privatAvtaleService: PrivatAvtaleService

    @BeforeEach
    fun setUp() {
        personRepository = stubPersonRepository()
        privatAvtaleService = PrivatAvtaleService(behandlingService, notatService, personRepository)
    }

    @Test
    fun `skal opprette privat avtale`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        privatAvtaleService.opprettPrivatAvtale(
            behandling.id!!,
            BarnDto(
                personident = Personident(behandling.søknadsbarn.first().ident!!),
            ),
        )

        assertSoftly {
            behandling.privatAvtale.shouldHaveSize(1)
            val opprettetPrivatAvtale = behandling.privatAvtale.first()
            opprettetPrivatAvtale.avtaleDato shouldBe null
            opprettetPrivatAvtale.skalIndeksreguleres shouldBe true
            opprettetPrivatAvtale.perioder shouldHaveSize 0
            opprettetPrivatAvtale.rolle.shouldNotBeNull()
        }
    }

    @Test
    fun `skal oppdatere privat avtale - legge til ny periode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )
        behandling.privatAvtale.add(privatAvtale)

        privatAvtaleService.oppdaterPrivatAvtale(
            behandling.id!!,
            privatAvtale.id!!,
            OppdaterePrivatAvtaleRequest(
                avtaleDato = LocalDate.parse("2024-01-01"),
                skalIndeksreguleres = false,
                oppdaterPeriode =
                    OppdaterePrivatAvtalePeriodeDto(
                        periode = DatoperiodeDto(LocalDate.parse("2024-01-01"), null),
                        beløp = BigDecimal(1000),
                    ),
            ),
        )

        assertSoftly {
            behandling.privatAvtale.shouldHaveSize(1)
            val oppdatertPrivatAvtale = behandling.privatAvtale.first()
            oppdatertPrivatAvtale.avtaleDato shouldBe LocalDate.parse("2024-01-01")
            oppdatertPrivatAvtale.skalIndeksreguleres shouldBe false
            oppdatertPrivatAvtale.perioder shouldHaveSize 1
            val nyPeriode = oppdatertPrivatAvtale.perioder.first()
            nyPeriode.fom shouldBe LocalDate.parse("2024-01-01")
            nyPeriode.tom shouldBe null
            nyPeriode.beløp shouldBe BigDecimal(1000)
        }
    }

    @Test
    fun `skal oppdatere privat avtale - oppdatere periode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )

        val periode =
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = null,
            )
        privatAvtale.perioder.add(periode)
        behandling.privatAvtale.add(privatAvtale)

        privatAvtaleService.oppdaterPrivatAvtale(
            behandling.id!!,
            privatAvtale.id!!,
            OppdaterePrivatAvtaleRequest(
                avtaleDato = LocalDate.parse("2024-01-01"),
                skalIndeksreguleres = false,
                oppdaterPeriode =
                    OppdaterePrivatAvtalePeriodeDto(
                        id = periode.id,
                        periode = DatoperiodeDto(LocalDate.parse("2024-07-01"), null),
                        beløp = BigDecimal(2000),
                    ),
            ),
        )

        assertSoftly {
            behandling.privatAvtale.shouldHaveSize(1)
            val oppdatertPrivatAvtale = behandling.privatAvtale.first()
            oppdatertPrivatAvtale.avtaleDato shouldBe LocalDate.parse("2024-01-01")
            oppdatertPrivatAvtale.skalIndeksreguleres shouldBe false
            oppdatertPrivatAvtale.perioder shouldHaveSize 1
            val nyPeriode = oppdatertPrivatAvtale.perioder.first()
            nyPeriode.fom shouldBe LocalDate.parse("2024-07-01")
            nyPeriode.tom shouldBe null
            nyPeriode.beløp shouldBe BigDecimal(2000)
        }
    }

    @Test
    fun `skal oppdatere privat avtale - slette periode`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )

        val periode =
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = null,
            )
        privatAvtale.perioder.add(periode)
        behandling.privatAvtale.add(privatAvtale)

        privatAvtaleService.oppdaterPrivatAvtale(
            behandling.id!!,
            privatAvtale.id!!,
            OppdaterePrivatAvtaleRequest(
                avtaleDato = LocalDate.parse("2024-01-01"),
                skalIndeksreguleres = false,
                slettePeriodeId = periode.id,
            ),
        )

        assertSoftly {
            behandling.privatAvtale.shouldHaveSize(1)
            val oppdatertPrivatAvtale = behandling.privatAvtale.first()
            oppdatertPrivatAvtale.avtaleDato shouldBe LocalDate.parse("2024-01-01")
            oppdatertPrivatAvtale.skalIndeksreguleres shouldBe false
            oppdatertPrivatAvtale.perioder.shouldHaveSize(0)
        }
    }

    @Test
    fun `skal oppdatere privat avtale - oppdater begrunnelse`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        every { behandlingService.hentBehandlingById(any()) } returns behandling
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )

        val periode =
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = null,
            )
        privatAvtale.perioder.add(periode)
        behandling.privatAvtale.add(privatAvtale)

        privatAvtaleService.oppdaterPrivatAvtale(
            behandling.id!!,
            privatAvtale.id!!,
            OppdaterePrivatAvtaleRequest(
                avtaleDato = LocalDate.parse("2024-01-01"),
                skalIndeksreguleres = false,
                begrunnelse = "DEtte er test",
            ),
        )

        assertSoftly {
            behandling.privatAvtale.shouldHaveSize(1)
            val oppdatertPrivatAvtale = behandling.privatAvtale.first()
            oppdatertPrivatAvtale.avtaleDato shouldBe LocalDate.parse("2024-01-01")
            oppdatertPrivatAvtale.skalIndeksreguleres shouldBe false
            val notat = behandling.notater.find { it.type == NotatGrunnlag.NotatType.PRIVAT_AVTALE }
            notat.shouldNotBeNull()
            notat.innhold shouldBe "DEtte er test"
        }
    }

    @Test
    fun `skal validere privat avtale ingen løpende`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        behandling.leggTilNotat("Test", NotatGrunnlag.NotatType.PRIVAT_AVTALE, rolle = behandling.søknadsbarn.first())
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = YearMonth.parse("2024-07"),
            ),
        )

        assertSoftly(privatAvtale.validerePrivatAvtale()) {
            it.ingenLøpendePeriode shouldBe true
            it.overlappendePerioder.shouldHaveSize(0)
            it.manglerAvtaledato shouldBe false
            it.manglerBegrunnelse shouldBe false
        }
    }

    @Test
    fun `skal validere privat avtale overlappende perioder`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        behandling.leggTilNotat("Test", NotatGrunnlag.NotatType.PRIVAT_AVTALE, rolle = behandling.søknadsbarn.first())
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = YearMonth.parse("2024-07"),
            ),
        )

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-05"),
                beløp = BigDecimal(1000),
                tom = null,
            ),
        )

        assertSoftly(privatAvtale.validerePrivatAvtale()) {
            it.ingenLøpendePeriode shouldBe false
            it.overlappendePerioder.shouldHaveSize(1)
            it.overlappendePerioder
                .first()
                .periode.fom shouldBe LocalDate.parse("2024-05-01")
            it.overlappendePerioder
                .first()
                .periode.til shouldBe LocalDate.parse("2024-07-31")
            it.manglerAvtaledato shouldBe false
            it.manglerBegrunnelse shouldBe false
        }
    }

    @Test
    fun `skal validere privat avtale  overlapper med løpende`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        behandling.leggTilNotat("Test", NotatGrunnlag.NotatType.PRIVAT_AVTALE, rolle = behandling.søknadsbarn.first())
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2023-04"), YearMonth.parse("2024-05")),
                    beløp = BigDecimal("6800"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2024-05"), null),
                    beløp = BigDecimal("5600"),
                ),
            ),
            2024,
        )

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = null,
            ),
        )

        assertSoftly(privatAvtale.validerePrivatAvtale()) {
            it.ingenLøpendePeriode shouldBe false
            it.perioderOverlapperMedLøpendeBidrag shouldHaveSize 1
            it.overlappendePerioder.shouldHaveSize(0)
            it.manglerAvtaledato shouldBe false
            it.manglerAvtaletype shouldBe false
            it.manglerBegrunnelse shouldBe false
        }
    }

    @Test
    fun `skal validere privat avtale når det ikke overlapper men har tom dato`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        behandling.leggTilNotat("Test", NotatGrunnlag.NotatType.PRIVAT_AVTALE, rolle = behandling.søknadsbarn.first())
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )
        behandling.leggTilGrunnlagBeløpshistorikk(
            Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG,
            behandling.søknadsbarn.first(),
            listOf(
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2023-04"), YearMonth.parse("2024-05")),
                    beløp = BigDecimal("6800"),
                ),
                opprettStønadPeriodeDto(
                    ÅrMånedsperiode(YearMonth.parse("2024-05"), null),
                    beløp = BigDecimal("5600"),
                ),
            ),
            2024,
        )

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2023-01"),
                beløp = BigDecimal(1000),
                tom = YearMonth.parse("2023-03"),
            ),
        )

        assertSoftly(privatAvtale.validerePrivatAvtale()) {
            it.ingenLøpendePeriode shouldBe false
            it.perioderOverlapperMedLøpendeBidrag shouldHaveSize 0
            it.overlappendePerioder.shouldHaveSize(0)
            it.manglerAvtaledato shouldBe false
            it.manglerAvtaletype shouldBe false
            it.manglerBegrunnelse shouldBe false
        }
    }

    @Test
    fun `skal validere privat avtale manglende begrunnelse og avtaledato`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.BIDRAG, generateId = true)
        val privatAvtale =
            opprettPrivatAvtale(
                behandling = behandling,
                person = testdataBarn1,
            )
        privatAvtale.avtaleDato = null
        privatAvtale.avtaleType = null

        privatAvtale.perioder.add(
            opprettPrivatAvtalePeriode(
                privatAvtale = privatAvtale,
                fom = YearMonth.parse("2024-01"),
                beløp = BigDecimal(1000),
                tom = null,
            ),
        )

        assertSoftly(privatAvtale.validerePrivatAvtale()) {
            it.ingenLøpendePeriode shouldBe false
            it.overlappendePerioder.shouldHaveSize(0)
            it.manglerAvtaledato shouldBe true
            it.manglerAvtaletype shouldBe true
            it.manglerBegrunnelse shouldBe true
        }
    }
}
