package no.nav.bidrag.behandling.controller.v1.behandling

import no.nav.bidrag.behandling.controller.v1.KontrollerTestRunner
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

data class OpprettBehandlingRequestTest(
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype,
    val søktFomDato: LocalDate,
    val mottattdato: LocalDate,
    val søknadFra: SøktAvType,
    val saksnummer: String?,
    val behandlerenhet: String,
    val roller: Set<OpprettRolleDto>,
)

@Suppress("NonAsciiCharacters")
class BehandlingControllerTest : KontrollerTestRunner() {
    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    companion object {
        fun oppretteBehandlingRequestTest(
            saksnummer: String?,
            enhet: String,
            roller: Set<OpprettRolleDto>,
        ): OpprettBehandlingRequestTest {
            return OpprettBehandlingRequestTest(
                Vedtakstype.FASTSETTELSE,
                Stønadstype.FORSKUDD,
                LocalDate.now().minusMonths(4),
                LocalDate.now(),
                SøktAvType.BIDRAGSMOTTAKER,
                saksnummer,
                enhet,
                roller,
            )
        }
    }
}
