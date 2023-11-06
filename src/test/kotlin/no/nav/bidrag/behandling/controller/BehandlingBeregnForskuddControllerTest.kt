package no.nav.bidrag.behandling.controller

import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.domene.enums.Rolletype
import no.nav.bidrag.domene.enums.SøktAvType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.*
import kotlin.test.Ignore

class BehandlingBeregnForskuddControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer

    fun prepareBehandling(): Behandling {
        val cal1 = Calendar.getInstance()
        cal1.set(Calendar.DAY_OF_MONTH, 1)

        val datoFom = cal1.time

        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, 2)
        val datoTom = cal.time

        val b =
            Behandling(
                BehandlingType.FORSKUDD,
                SoknadType.FASTSETTELSE,
                datoFom,
                datoTom,
                datoFom,
                "1234",
                123213L,
                123213L,
                "ENHE",
                SøktAvType.BIDRAGSMOTTAKER,
                null,
                null,
                null,
                datoFom,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
            )

        val husstandsBarn =
            HusstandsBarn(
                behandling = b,
                medISaken = true,
                null,
                "123",
                null,
                datoFom,
            )
        husstandsBarn.perioder =
            mutableSetOf(
                HusstandsBarnPeriode(
                    husstandsBarn,
                    datoFom,
                    datoTom,
                    BoStatusType.REGISTRERT_PA_ADRESSE,
                    "",
                ),
            )

        b.husstandsBarn =
            mutableSetOf(
                husstandsBarn,
            )
        b.roller =
            mutableSetOf(
                Rolle(b, Rolletype.BIDRAGSMOTTAKER, "123", datoFom, null, null),
                Rolle(b, Rolletype.BARN, "123", datoFom, null, null),
            )
        b.inntekter =
            mutableSetOf(
                Inntekt("lønn", BigDecimal.TEN, datoFom, datoTom, "ident", true, true),
            )
        b.barnetillegg =
            mutableSetOf(
                Barnetillegg(b, "ident", BigDecimal.TEN, datoFom, datoTom),
            )
        b.utvidetbarnetrygd =
            mutableSetOf(
                Utvidetbarnetrygd(b, true, BigDecimal.TEN, datoFom, datoTom),
            )
        b.sivilstand =
            mutableSetOf(
                Sivilstand(b, datoFom, datoTom, SivilstandType.GIFT),
            )

        return b
    }

//    @Test
//    fun `split periods just one period`() {
//        val controller = BehandlingBeregnForskuddController(behandlingService, bidragBeregnForskuddConsumer)
//        val cal1 = Calendar.getInstance()
//        val cal2 = Calendar.getInstance()
//        cal1.set(Calendar.DAY_OF_MONTH, 1)
//        cal2.set(Calendar.DAY_OF_MONTH, 10)
//
//        val fraDato = cal1.time
//        val fraDato2 = cal2.time
//        cal1.add(Calendar.MONTH, 1)
//        cal2.add(Calendar.MONTH, 1)
//        val tilDao = cal1.time
//        val tilDao2 = cal2.time
//
//        val b = HusstandsBarn(prepareBehandling(), true)
//
//        val splitPeriods1 = controller.splitPeriods(
//            listOf(
//                prepareHusstandsBarnPeriode(b, fraDato, tilDao),
//                prepareHusstandsBarnPeriode(b, fraDato2, tilDao2),
//            ),
//        )
//
//        assertEquals(3, splitPeriods1.size)
//    }

//    @Test
//    fun `split periods`() {
//        val controller = BehandlingBeregnForskuddController(behandlingService, bidragBeregnForskuddConsumer)
//        val cal1 = Calendar.getInstance()
//        cal1.set(Calendar.DAY_OF_MONTH, 1)
//
//        val fraDato = cal1.time
//        cal1.add(Calendar.MONTH, 1)
//        val tilDao = cal1.time
//
//        val b = HusstandsBarn(prepareBehandling(), true)
//
//        val splitPeriods = controller.splitPeriods(
//            listOf(
//                prepareHusstandsBarnPeriode(b, fraDato, tilDao),
//            ),
//        )
//
//        assertEquals(1, splitPeriods.size)
//    }

    private fun prepareHusstandsBarnPeriode(
        b: HusstandsBarn,
        fraDato: Date,
        tilDao: Date,
    ) = HusstandsBarnPeriode(
        b,
        fraDato,
        tilDao,
        BoStatusType.REGISTRERT_PA_ADRESSE,
        "",
    )

    @Test
    @Ignore
    fun preparePayload() {
//        val c = BehandlingBeregnForskuddController(behandlingService, bidragBeregnForskuddConsumer)
//
//        val message = HttpEntity(c.preparePayload(prepareBehandling(), R))
//        val objectMapper = ObjectMapper()
//
//        objectMapper.writeValue(System.out, message.body)
    }
}
