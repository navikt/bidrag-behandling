package no.nav.bidrag.behandling.dto.v1.behandling

import no.nav.bidrag.domene.enums.rolle.Rolletype
import java.time.LocalDate

data class RolleDto(
    val id: Long,
    val rolletype: Rolletype,
    val ident: String? = null,
    val navn: String? = null,
    val fødselsdato: LocalDate? = null,
    val harInnvilgetTilleggsstønad: Boolean? = null,
    val delAvOpprinneligBehandling: Boolean?,
    val erRevurdering: Boolean?,
)
