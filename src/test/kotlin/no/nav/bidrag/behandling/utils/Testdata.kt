package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.transformers.toDate
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate
import java.time.YearMonth

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val ROLLE_BM = ForsendelseRolleDto(Personident("313213213"), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BA_1 = ForsendelseRolleDto(Personident("1344124"), type = Rolletype.BARN)
val ROLLE_BP = ForsendelseRolleDto(Personident("213244124"), type = Rolletype.BIDRAGSPLIKTIG)

fun opprettForsendelseResponsUnderOpprettelse(forsendelseId: Long = 1) =
    ForsendelseResponsTo(
        forsendelseId = forsendelseId,
        saksnummer = SAKSNUMMER,
        behandlingInfo =
            BehandlingInfoResponseDto(
                soknadId = SOKNAD_ID.toString(),
                erFattet = false,
            ),
        forsendelseType = ForsendelseTypeTo.UTGÅENDE,
        status = ForsendelseStatusTo.UNDER_OPPRETTELSE,
    )

fun oppretteBehandling(): Behandling {
    return Behandling(
        Behandlingstype.FORSKUDD,
        SoknadType.FASTSETTELSE,
        datoFom = YearMonth.now().atDay(1).minusMonths(16).toDate(),
        datoTom = YearMonth.now().plusMonths(10).atEndOfMonth().toDate(),
        mottatDato = LocalDate.now().toDate(),
        "123",
        123,
        null,
        "ENH",
        "Z9999",
        "Navn Navnesen",
        "bisys",
        SøktAvType.BIDRAGSMOTTAKER,
        null,
        null,
        virkningsDato = LocalDate.now().toDate(),
    )
}

fun oppretteBehandlingRoller(behandling: Behandling) =
    mutableSetOf(
        Rolle(
            ident = ROLLE_BM.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BIDRAGSMOTTAKER,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BP.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BIDRAGSPLIKTIG,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
            rolleType = Rolletype.BARN,
            behandling = behandling,
            fodtDato = null,
            opprettetDato = null,
        ),
    )
