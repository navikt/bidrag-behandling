package no.nav.bidrag.behandling.transformers

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.junit5.MockKExtension
import no.nav.bidrag.behandling.dto.v2.underhold.DatoperiodeDto
import no.nav.bidrag.behandling.transformers.underhold.finneOverlappendePerioder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate

@ExtendWith(MockKExtension::class)
class ValideringTest {
    @Nested
    open inner class OverlappendeDatoperioder {
        @Test
        fun `skal identifisere og gruppere perioder som overlapper`() {
            // gitt
            val førstePeriode = DatoperiodeDto(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 1))
            val periodeSomOverlapperFørstePeriode = DatoperiodeDto(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 8, 1))
            val periodeSomOverlapperFørsteOgAndrePeriode =
                DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1))
            val duplikatAvPeriodeSomOverlapperFørsteOgAndrePeriode = DatoperiodeDto(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 7, 1))
            val periodeSomIkkeOverlapperAndrePerioder = DatoperiodeDto(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 11, 1))

            // hvis
            val resultat =
                listOf(
                    periodeSomOverlapperFørstePeriode,
                    duplikatAvPeriodeSomOverlapperFørsteOgAndrePeriode,
                    periodeSomIkkeOverlapperAndrePerioder,
                    førstePeriode,
                    periodeSomOverlapperFørsteOgAndrePeriode,
                ).finneOverlappendePerioder()

            // så
            assertSoftly(resultat) {
                shouldNotBeNull()
                shouldHaveSize(2)
            }

            assertSoftly(resultat.map { it.periode }) {
                shouldNotBeNull()
                shouldHaveSize(2)
                contains(periodeSomOverlapperFørstePeriode) && contains(periodeSomOverlapperFørsteOgAndrePeriode)
            }

            assertSoftly(resultat.map { it.periode }) {
                shouldNotBeNull()
                shouldHaveSize(1)
                contains(periodeSomOverlapperFørsteOgAndrePeriode)
            }
        }
    }
}
