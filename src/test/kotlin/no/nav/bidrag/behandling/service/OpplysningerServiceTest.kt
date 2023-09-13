package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingType
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.SoknadFraType
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.domain.enums.EngangsbelopType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.Date
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
        val b = behandlingService.createBehandling(
            Behandling(
                BehandlingType.FORSKUDD,
                SoknadType.FASTSETTELSE,
                Date(1),
                Date(2),
                Date(2),
                "123",
                123L,
                null,
                "ENH1",
                SoknadFraType.VERGE,
                engangsbelopType = EngangsbelopType.ETTERGIVELSE,
                stonadType = null,
            ),
        )
        opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))
        opplysningerService.opprett(b.id!!, OpplysningerType.BOFORHOLD, "data", Date(1))

        assertTrue(opplysningerService.hentSistAktiv(1, OpplysningerType.BOFORHOLD).isPresent)
    }
}
