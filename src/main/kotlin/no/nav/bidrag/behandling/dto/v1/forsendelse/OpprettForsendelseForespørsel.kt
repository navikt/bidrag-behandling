package no.nav.bidrag.behandling.dto.v1.forsendelse

data class OpprettForsendelseForespørsel(
    val mottaker: MottakerDto? = null,
    val gjelderIdent: String? = null,
    val saksnummer: String? = null,
    val enhet: String? = null,
    val språk: String = "NB",
    val tema: String? = null,
    val behandlingInfo: BehandlingInfoDto? = null,
    val opprettTittel: Boolean = true,
)
