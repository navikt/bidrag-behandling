package no.nav.bidrag.behandling.controller

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SivilstandType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.service.BehandlingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import java.math.BigDecimal
import java.util.Calendar

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

        val b = Behandling(
            BehandlingType.FORSKUDD,
            SoknadType.FASTSETTELSE,
            datoFom,
            datoTom,
            datoFom,
            "1234",
            "ENHE",
            SoknadFraType.BIDRAGSMOTTAKER,
            datoFom,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
        )

        val behandlingBarn = BehandlingBarn(
            behandling = b,
            medISaken = true,
            null,
            "123",
            null,
            datoFom,
        )
        behandlingBarn.perioder = mutableSetOf(
            BehandlingBarnPeriode(
                behandlingBarn,
                datoFom,
                datoTom,
                BoStatusType.DOKUMENTERT_BOENDE_HOS_BM,
                "",
            ),
        )

        b.behandlingBarn = mutableSetOf(
            behandlingBarn,
        )
        b.roller = mutableSetOf(
            Rolle(b, RolleType.BIDRAGS_MOTTAKER, "123", datoFom, null, null),
            Rolle(b, RolleType.BARN, "123", datoFom, null, null),
        )
        b.inntekter = mutableSetOf(
            Inntekt(b, true, "beskrivelse", BigDecimal.TEN, datoFom, datoTom, "ident", true),
        )
        b.barnetillegg = mutableSetOf(
            Barnetillegg(b, "ident", BigDecimal.TEN, datoFom, datoTom),
        )
        b.utvidetbarnetrygd = mutableSetOf(
            Utvidetbarnetrygd(b, true, BigDecimal.TEN, datoFom, datoTom),
        )
        b.sivilstand = mutableSetOf(
            Sivilstand(b, datoFom, datoTom, SivilstandType.GIFT),
        )

        return b
    }

    @Test
    fun preparePayload() {
        val c = BehandlingBeregnForskuddController(behandlingService, bidragBeregnForskuddConsumer)

        val message = HttpEntity(c.preparePayload(prepareBehandling()))
        val objectMapper = ObjectMapper()

//        objectMapper.writeValue(File("./beregn.json"), message.body)
        objectMapper.writeValue(System.out, message.body)
    }
}
