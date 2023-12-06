package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
<<<<<<< HEAD
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
=======
import no.nav.bidrag.behandling.database.datamodell.SoknadType
>>>>>>> main
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpplysningerServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var opplysningerService: OpplysningerService

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `hente opplysninger`() {
        val res = opplysningerService.hentSistAktiv(1, OpplysningerType.BOFORHOLD)
        assertFalse(res.isPresent)
    }

    @Test
    fun `skal være bare en rad med aktive opplysninger`() {
        val b =
            behandlingService.createBehandling(
                Behandling(
                    Behandlingstype.FORSKUDD,
                    Soknadstype.FASTSETTELSE,
                    datoFom = LocalDate.now().minusMonths(3),
                    datoTom = LocalDate.now().plusMonths(2),
                    mottattdato = LocalDate.now(),
                    "123",
                    123L,
                    null,
                    "ENH1",
                    SøktAvType.VERGE,
                    engangsbeloptype = Engangsbeløptype.ETTERGIVELSE,
                    stonadstype = null,
                ),
            )
        val opp4 = opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))

        val option = opplysningerService.hentSistAktiv(b.id!!, OpplysningerType.BOFORHOLD)

        assertTrue(option.isPresent)
        assertEquals(opp4.id, option.get().id)
    }
}
