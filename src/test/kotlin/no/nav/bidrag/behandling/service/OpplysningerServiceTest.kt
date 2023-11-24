package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.domene.enums.Engangsbeløptype
import no.nav.bidrag.domene.enums.SøktAvType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
                    SoknadType.FASTSETTELSE,
                    Date(1),
                    Date(2),
                    Date(2),
                    "123",
                    123L,
                    null,
                    "ENH1",
                    SøktAvType.VERGE,
                    engangsbelopType = Engangsbeløptype.ETTERGIVELSE,
                    stonadType = null,
                ),
            )
        val opp1 = opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))
        val opp2 = opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))
        val opp4 = opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))
        val opp3 =
            opplysningerService.opprett(
                b.id!!,
                OpplysningerType.INNTEKTSOPPLYSNINGER,
                "data",
                Date(1),
            )

        val option = opplysningerService.hentSistAktiv(b.id!!, OpplysningerType.BOFORHOLD)

        assertTrue(option.isPresent)
        assertEquals(opp4.id, option.get().id)
    }
}
