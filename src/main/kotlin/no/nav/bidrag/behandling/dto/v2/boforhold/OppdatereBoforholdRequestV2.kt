package no.nav.bidrag.behandling.dto.v2.boforhold

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate

@Schema(description = "Oppdaterer husstandsbarn, sivilstand, eller notat")
data class OppdatereBoforholdRequestV2(
    val oppdatereHusstandsbarn: OppdatereHusstandsbarn? = null,
    val oppdatereSivilstand: OppdatereSivilstand? = null,
    val oppdatereNotat: OppdaterNotat? = null,
)

data class OppdatereBoforholdResponse(
    @Schema(description = "Husstandsbarn som ble opprettet")
    val oppdatertHusstandsbarn: HusstandsbarnDtoV2? = null,
    val oppdatertSivilstand: SivilstandDto? = null,
    val oppdatertNotat: OppdaterNotat? = null,
    val valideringsfeil: BoforholdValideringsfeil,
)

data class OppdatereHusstandsbarn(
    val nyttHusstandsbarn: PersonaliaHusstandsbarn? = null,
    val nyHusstandsbarnperiode: Husstandsbarnperiode? = null,
    val sletteHusstandsbarnperiode: Long? = null,
    val sletteHusstandsbarn: Long? = null,
)

data class Husstandsbarnperiode(
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
