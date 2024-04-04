package no.nav.bidrag.behandling.dto.v2.boforhold

import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate

data class OppdatereBoforholdRequestV2(
    val oppdatereHusstandsbarn: OppdatereHusstandsbarn? = null,
    val oppdatereSivilstand: OppdatereSivilstand? = null,
    val oppdatereNotat: OppdaterNotat? = null,
)

data class OppdatereHusstandsbarn(
    val nyttHusstandsbarn: PersonaliaHusstandsbarn? = null,
    val nyBostatusperiode: Bostatusperiode? = null,
    val sletteHusstandsbarn: Long? = null,
)

data class Bostatusperiode(
    val idHusstandsbarn: Long,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val bostatus: Bostatuskode,
)

data class PersonaliaHusstandsbarn(
    val personident: Personident? = null,
    val fødselsdato: LocalDate,
    val navn: String? = null,
)

data class OppdatereSivilstand(
    val leggeTilSivilstandsperiode: Sivilstandsperiode? = null,
    val sletteSivilstandsperiode: Long? = null,
)

data class Sivilstandsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate? = null,
    val sivilstand: Sivilstandskode,
)
