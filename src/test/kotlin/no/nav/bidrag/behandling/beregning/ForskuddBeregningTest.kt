package no.nav.bidrag.behandling.beregning

import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.beregning.model.HusstandsBarnPeriodeModel
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.junit.jupiter.api.Test
import java.util.Calendar

class ForskuddBeregningTest {
    @Test
    fun `split periods just one period`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)
        cal2.set(Calendar.DAY_OF_MONTH, 10)

        val fraDato = cal1.time
        val fraDato2 = cal2.time
        cal1.add(Calendar.MONTH, 1)
        cal2.add(Calendar.MONTH, 1)
        val tilDato = cal1.time
        val tilDato2 = cal2.time

        val splitPeriods1 =
            forskuddBeregning.splitPeriods(
                listOf(
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato2.toLocalDate(), tilDato2.toLocalDate(), "ident2", BoStatusType.REGISTRERT_PA_ADRESSE),
                ),
            )

        splitPeriods1.size shouldBe 3
    }

    @Test
    fun `split periods`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato = cal1.time
        cal1.add(Calendar.MONTH, 1)
        val tilDato = cal1.time

        val splitPeriods =
            forskuddBeregning.splitPeriods(
                listOf(
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
                ),
            )

        splitPeriods.size shouldBe 1
    }

    @Test
    fun `tre barn samtidig`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato = cal1.time
        cal1.add(Calendar.MONTH, 1)
        val tilDato = cal1.time

        val splitPeriods =
            forskuddBeregning.splitPeriods(
                listOf(
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident2", BoStatusType.REGISTRERT_PA_ADRESSE),
                ),
            )

        splitPeriods.size shouldBe 1
        splitPeriods[0].antall shouldBe 3.0
    }

    @Test
    fun `tre barn men bare to barn samtidig`() {
        val forskuddBeregning = ForskuddBeregning()

        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato1 = cal1.time
        cal1.add(Calendar.YEAR, 1)
        val tilDato1 = cal1.time

        val cal2 = Calendar.getInstance()
        cal2.set(Calendar.DAY_OF_MONTH, 20)

        val fraDato2 = cal2.time
        cal2.add(Calendar.MONTH, 1)
        val tilDato2 = cal2.time

        cal2.add(Calendar.MONTH, 1)
        val fraDato3 = cal2.time

        cal2.add(Calendar.MONTH, 1)
        val tilDato3 = cal2.time

        val splitPeriods =
            forskuddBeregning.splitPeriods(
                listOf(
                    HusstandsBarnPeriodeModel(fraDato1.toLocalDate(), tilDato1.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato2.toLocalDate(), tilDato2.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato3.toLocalDate(), tilDato3.toLocalDate(), "ident2", BoStatusType.REGISTRERT_PA_ADRESSE),
                ),
            )

        splitPeriods.size shouldBe 5
        splitPeriods[0].antall shouldBe 1.0
        splitPeriods[1].antall shouldBe 2.0
        splitPeriods[2].antall shouldBe 1.0
        splitPeriods[3].antall shouldBe 2.0
        splitPeriods[4].antall shouldBe 1.0
    }

    @Test
    fun `to barn ikke samtidig`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato = cal1.time
        cal1.add(Calendar.MONTH, 1)
        val tilDato = cal1.time

        cal1.add(Calendar.MONTH, 1)
        val fraDato1 = cal1.time

        cal1.add(Calendar.MONTH, 1)
        val tilDato1 = cal1.time

        val splitPeriods =
            forskuddBeregning.splitPeriods(
                listOf(
                    HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDato.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
                    HusstandsBarnPeriodeModel(fraDato1.toLocalDate(), tilDato1.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                ),
            )

        splitPeriods.size shouldBe 2
    }
}
