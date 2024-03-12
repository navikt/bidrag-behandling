package no.nav.bidrag.behandling.controller.behandling

import no.nav.bidrag.behandling.controller.KontrollerTestRunner
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.GrunnlagRepository
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
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

    @Autowired
    lateinit var grunnlagRepository: GrunnlagRepository

    companion object {
        fun oppretteBehandlingRequestTest(
            saksnummer: String?,
            enhet: String,
            roller: Set<OpprettRolleDto>,
            søknadsid: Long = 100,
        ): OpprettBehandlingRequest {
            return OpprettBehandlingRequest(
                Vedtakstype.FASTSETTELSE,
                stønadstype = Stønadstype.FORSKUDD,
                søktFomDato = LocalDate.now().minusMonths(4),
                mottattdato = LocalDate.now(),
                søknadFra = SøktAvType.BIDRAGSMOTTAKER,
                saksnummer = saksnummer ?: SAKSNUMMER,
                behandlerenhet = enhet,
                roller = roller,
                engangsbeløpstype = null,
                søknadsid = søknadsid,
            )
        }
    }
}
