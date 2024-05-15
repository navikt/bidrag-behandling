package no.nav.bidrag.behandling.service

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.persistence.EntityManager
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.utils.testdata.opprettInntekt
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional

@ExtendWith(MockKExtension::class)
class InntektServiceMockTest {
    @MockK
    lateinit var behandlingRepository: BehandlingRepository

    @MockK
    lateinit var inntektRepository: InntektRepository

    @MockK(relaxed = true)
    lateinit var entityManager: EntityManager

    lateinit var inntektService: InntektService

    @BeforeEach
    fun initMock() {
        inntektService = InntektService(behandlingRepository, inntektRepository, entityManager)
    }

    @Test
    fun `skal oppdatere periode på inntekter etter endring i virkningstidspunkt`() {
        val behandling = oppretteBehandling(1)
        val virkningstidspunkt = LocalDate.parse("2023-07-01")
        behandling.inntekter =
            mutableSetOf(
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2023-06"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-08"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2023-01"),
                    datoTom = YearMonth.parse("2024-07"),
                    type = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                    kilde = Kilde.MANUELL,
                ),
                opprettInntekt(
                    behandling = behandling,
                    datoFom = YearMonth.parse("2024-01"),
                    datoTom = null,
                    type = Inntektsrapportering.AINNTEKT,
                ),
            )

        behandling.virkningstidspunkt = virkningstidspunkt
        every { behandlingRepository.findBehandlingById(any()) } returns Optional.of(behandling)

        inntektService.rekalkulerPerioderInntekter(behandling.id!!)

        val inntekter = behandling.inntekter.toList()
        assertSoftly(inntekter[0]) {
            taMed shouldBe false
            datoFom shouldBe null
            datoTom shouldBe null
        }
        assertSoftly(inntekter[1]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2023-08-01")
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[2]) {
            taMed shouldBe true
            datoFom shouldBe virkningstidspunkt
            datoTom shouldBe LocalDate.parse("2024-07-31")
        }
        assertSoftly(inntekter[3]) {
            taMed shouldBe true
            datoFom shouldBe LocalDate.parse("2024-01-01")
            datoTom shouldBe null
        }
    }
}
