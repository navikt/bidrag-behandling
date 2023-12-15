package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.Grunnlagstype
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GrunnlagServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var grunnlagService: GrunnlagService

    @Autowired
    lateinit var behandlingService: BehandlingService

    @Test
    fun `hente opplysninger`() {
        val res = grunnlagService.hentSistAktiv(1, Grunnlagstype.BOFORHOLD)
        assertNull(res)
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
                    "Z9999",
                    "Navn Navnesen",
                    "bisys",
                    SøktAvType.VERGE,
                    engangsbeloptype = Engangsbeløptype.ETTERGIVELSE,
                    stonadstype = null,
                ),
            )
        val opp4 = grunnlagService.opprett(b.id!!, Grunnlagstype.BOFORHOLD, "data", LocalDateTime.now())

        val opplysninger = grunnlagService.hentSistAktiv(b.id!!, Grunnlagstype.BOFORHOLD)

        assertNotNull(opplysninger)
        assertEquals(opp4.id, opplysninger.id)
    }
}
