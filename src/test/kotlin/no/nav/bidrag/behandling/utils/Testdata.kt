package no.nav.bidrag.behandling.utils

import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.transport.sak.RolleDto

val SAKSNUMMER = "1233333"
val SOKNAD_ID = 12412421414L
val ROLLE_BM = ForsendelseRolleDto(PersonIdent("313213213"), type = Rolletype.BM)
val ROLLE_BA_1 = ForsendelseRolleDto(PersonIdent("1344124"), type = Rolletype.BA)
val ROLLE_BA_2 = ForsendelseRolleDto(PersonIdent("12344424214"), type = Rolletype.BA)
val ROLLE_BP = ForsendelseRolleDto(PersonIdent("213244124"), type = Rolletype.BP)
