package no.nav.bidrag.behandling.dto.v1.behandling

import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import java.time.LocalDate
import java.time.YearMonth

data class RolleDto(
    val id: Long,
    val rolletype: Rolletype,
    val ident: String? = null,
    val navn: String? = null,
    val fødselsdato: LocalDate? = null,
    val harInnvilgetTilleggsstønad: Boolean? = null,
    val delAvOpprinneligBehandling: Boolean?,
    val erRevurdering: Boolean?,
    val stønadstype: Stønadstype?,
    val saksnummer: String,
    val beregnFraDato: YearMonth? = null,
    val beregnTilDato: YearMonth? = null,
    val bidragsmottaker: String? = null,
)
