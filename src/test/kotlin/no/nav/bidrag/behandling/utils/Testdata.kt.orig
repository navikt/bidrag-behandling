package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Behandlingstype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
<<<<<<< HEAD
=======
import no.nav.bidrag.behandling.transformers.toDate
>>>>>>> main
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
        Soknadstype.FASTSETTELSE,
        datoFom = YearMonth.now().atDay(1).minusMonths(16),
        datoTom = YearMonth.now().plusMonths(10).atEndOfMonth(),
        mottattdato = LocalDate.now(),
        "1900000",
        123,
        null,
        "ENH",
        SøktAvType.BIDRAGSMOTTAKER,
        null,
        null,
        virkningsdato = LocalDate.now(),
    )
}

fun oppretteBehandlingRoller(behandling: Behandling) =
    mutableSetOf(
        Rolle(
            ident = ROLLE_BM.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BIDRAGSMOTTAKER,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(29 * 13),
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BP.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BIDRAGSPLIKTIG,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(33 * 11),
            opprettetDato = null,
        ),
        Rolle(
            ident = ROLLE_BA_1.fødselsnummer?.verdi!!,
            rolletype = Rolletype.BARN,
            behandling = behandling,
            foedselsdato = LocalDate.now().minusMonths(3 * 14),
            opprettetDato = null,
        ),
    )
