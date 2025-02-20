package no.nav.bidrag.behandling.consumer.dto

import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.util.VirkedagerProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PARAMETER_OPPGAVE_TYPE = "oppgavetype"
private const val PARAMETER_SAKSREFERANSE = "saksreferanse"
private const val PARAMETER_AKTOERID = "aktoerId"
private const val PARAMETER_TEMA = "tema"
private const val PARAMETER_TILDELT_ENHET = "tildeltEnhetsnr"
private val NORSK_TIDSSTEMPEL_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

val behandlingstypeNasjonal = "ae0118"
val behandlingstypeUtland = "ae0106"

@Suppress("unused") // used by jackson...
data class OpprettOppgaveRequest(
    var beskrivelse: String,
    var oppgavetype: OppgaveType = OppgaveType.JFR,
    var opprettetAvEnhetsnr: String = "9999",
    var prioritet: String = Prioritet.HOY.name,
    var tema: String = "BID",
    var aktivDato: String = formatterDatoForOppgave(LocalDate.now()),
    var fristFerdigstillelse: String = formatterDatoForOppgave(VirkedagerProvider.nesteVirkedag()),
    open var tildeltEnhetsnr: String? = null,
    open val saksreferanse: String? = null,
    open val journalpostId: String? = null,
    open val tilordnetRessurs: String? = null,
    open val personident: String? = null,
    var bnr: String? = null,
    val behandlingstype: String? = if (tildeltEnhetsnr == "4865") behandlingstypeUtland else behandlingstypeNasjonal,
)

fun formatterDatoForOppgave(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("uuuu-MM-dd"))

enum class Prioritet {
    HOY, // , NORM, LAV
}

data class OppgaveSokResponse(
    var antallTreffTotalt: Int = 0,
    var oppgaver: List<OppgaveDto> = emptyList(),
)

data class OppgaveDto(
    val id: Long,
    val tildeltEnhetsnr: String? = null,
    val endretAvEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val journalpostId: String? = null,
    val journalpostkilde: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val bnr: String? = null,
    val samhandlernr: String? = null,
    val aktoerId: String? = null,
    val orgnr: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val temagruppe: String? = null,
    val tema: String? = null,
    val behandlingstema: String? = null,
    val oppgavetype: String? = null,
    val behandlingstype: String? = null,
    val versjon: Int = -1,
    val mappeId: String? = null,
    val fristFerdigstillelse: LocalDate? = null,
    val aktivDato: String? = null,
    val opprettetTidspunkt: String? = null,
    val opprettetAv: String? = null,
    val endretAv: String? = null,
    val ferdigstiltTidspunkt: String? = null,
    val endretTidspunkt: String? = null,
    val prioritet: String? = null,
    val status: OppgaveStatus? = null,
    val metadata: Map<String, String>? = null,
)

data class OppgaveSokRequest(
    private val parametre: StringBuilder = StringBuilder(),
) {
    fun søkForGenerellOppgave(): OppgaveSokRequest = leggTilParameter(PARAMETER_OPPGAVE_TYPE, OppgaveType.GEN.name)

    fun leggTilFagomrade(fagomrade: String): OppgaveSokRequest = leggTilParameter(PARAMETER_TEMA, fagomrade)

    fun leggTilEnhet(enhet: String): OppgaveSokRequest = leggTilParameter(PARAMETER_TILDELT_ENHET, enhet)

    fun leggTilSaksreferanse(saksnummer: String?): OppgaveSokRequest {
        leggTilParameter(PARAMETER_SAKSREFERANSE, saksnummer)
        return this
    }

    fun leggTilAktoerId(aktoerId: String?): OppgaveSokRequest {
        leggTilParameter(PARAMETER_AKTOERID, aktoerId)
        return this
    }

    private fun leggTilParameter(
        navn: String?,
        verdi: Any?,
    ): OppgaveSokRequest {
        if (parametre.isNotEmpty()) {
            parametre.append('&')
        }

        parametre.append(navn).append('=').append(verdi)

        return this
    }

    fun hentParametre(): String =
        "$parametre&tema=BID&tema=FAR&statuskategori=AAPEN&sorteringsrekkefolge=ASC&sorteringsfelt=FRIST&limit=100"
}

enum class OppgaveStatus {
    FERDIGSTILT,
    AAPNET,
    OPPRETTET,
    FEILREGISTRERT,
    UNDER_BEHANDLING,
}

enum class OppgaveType(
    val description: String,
) {
    BEH_SAK("Behandle sak"),
    VUR("Vurder dokument"),
    GEN("Generell"),
    JFR("Journalføring"),
    RETUR("Retur"),
    VURD_HENV("Vurder henvendelse"),
}

internal fun lagBeskrivelseHeader(
    saksbehandlerIdent: String,
    enhet: String,
): String {
    val dateFormatted = LocalDateTime.now().format(NORSK_TIDSSTEMPEL_FORMAT)
    val saksbehandlerNavn = SaksbehandlernavnProvider.hentSaksbehandlernavn(saksbehandlerIdent)
    val saksbehandlersInfo = "${saksbehandlerNavn ?: saksbehandlerIdent} ($saksbehandlerIdent, $enhet)"
    return "--- $dateFormatted $saksbehandlersInfo ---\r\n"
}
