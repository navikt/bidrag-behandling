package no.nav.bidrag.behandling.beregning

import no.nav.bidrag.behandling.beregning.model.HusstandsBarnPeriodeModel
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.junit.jupiter.api.Assertions.assertEquals
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
        val tilDao = cal1.time
        val tilDao2 = cal2.time

        val splitPeriods1 = forskuddBeregning.splitPeriods(
            listOf(
                HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDao.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                HusstandsBarnPeriodeModel(fraDato2.toLocalDate(), tilDao2.toLocalDate(), "ident2", BoStatusType.REGISTRERT_PA_ADRESSE),
            ),
        )

        assertEquals(3, splitPeriods1.size)
    }

    @Test
    fun `split periods`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato = cal1.time
        cal1.add(Calendar.MONTH, 1)
        val tilDao = cal1.time

        val splitPeriods = forskuddBeregning.splitPeriods(
            listOf(
                HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDao.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
            ),
        )

        assertEquals(1, splitPeriods.size)
    }

    @Test
    fun `tre barn i samtidig`() {
        val forskuddBeregning = ForskuddBeregning()
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val fraDato = cal1.time
        cal1.add(Calendar.MONTH, 1)
        val tilDao = cal1.time

        val splitPeriods = forskuddBeregning.splitPeriods(
            listOf(
                HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDao.toLocalDate(), "ident", BoStatusType.REGISTRERT_PA_ADRESSE),
                HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDao.toLocalDate(), "ident1", BoStatusType.REGISTRERT_PA_ADRESSE),
                HusstandsBarnPeriodeModel(fraDato.toLocalDate(), tilDao.toLocalDate(), "ident2", BoStatusType.REGISTRERT_PA_ADRESSE),
            ),
        )

        assertEquals(1, splitPeriods.size)
        assertEquals(3.0, splitPeriods.get(0).antall)
    }
}
