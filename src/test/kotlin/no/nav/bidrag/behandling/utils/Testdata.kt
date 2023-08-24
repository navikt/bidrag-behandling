package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.consumer.BehandlingInfoResponseDto
import no.nav.bidrag.behandling.consumer.ForsendelseResponsTo
import no.nav.bidrag.behandling.consumer.ForsendelseStatusTo
import no.nav.bidrag.behandling.consumer.ForsendelseTypeTo
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.transport.sak.RolleDto

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val ROLLE_BM = ForsendelseRolleDto(PersonIdent("313213213"), type = Rolletype.BIDRAGSMOTTAKER)
val ROLLE_BA_1 = ForsendelseRolleDto(PersonIdent("1344124"), type = Rolletype.BARN)
val ROLLE_BA_2 = ForsendelseRolleDto(PersonIdent("12344424214"), type = Rolletype.BARN)
val ROLLE_BP = ForsendelseRolleDto(PersonIdent("213244124"), type = Rolletype.BIDRAGSPLIKTIG)


fun opprettForsendelseResponsUnderOpprettelse(forsendelseId: Long = 1)=ForsendelseResponsTo(
    forsendelseId = forsendelseId,
    saksnummer = SAKSNUMMER,
    behandlingInfo = BehandlingInfoResponseDto(
        soknadId = SOKNAD_ID.toString(),
        erFattet = false
    ),
    forsendelseType = ForsendelseTypeTo.UTGÅENDE,
    status = ForsendelseStatusTo.UNDER_OPPRETTELSE
)