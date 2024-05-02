package no.nav.bidrag.behandling.dto.v2.boforhold

import com.fasterxml.jackson.annotation.JsonFormat
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
    val oppdatereHusstandsmedlem: OppdatereHusstandsmedlem? = null,
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

data class OppdatereHusstandsmedlem(
    @Schema(description = "Informasjon om husstandsmedlem som skal opprettes")
    val opprettHusstandsmedlem: OpprettHusstandsstandsmedlem? = null,
    val oppdaterPeriode: OppdaterHusstandsmedlemPeriode? = null,
    @Schema(type = "Long", description = "Id til perioden som skal slettes")
    val slettPeriode: Long? = null,
    @Schema(type = "Long", description = "Id til husstandsmedlemmet som skal slettes")
    val slettHusstandsmedlem: Long? = null,
    @Schema(
        type = "Long",
        description = """Id til husstandsmedlemmet perioden skal resettes for. 
        |Dette vil resette til opprinnelig perioder hentet fra offentlige registre""",
    )
    val tilbakestillPerioderForHusstandsmedlem: Long? = null,
    @Schema(type = "Long", description = "Id til husstandsmedlemmet siste steg skal angres for")
    val angreSisteStegForHusstandsmedlem: Long? = null,
)

data class OppdaterHusstandsmedlemPeriode(
    @Schema(type = "Long", description = "Id til husstandsbarnet perioden skal gjelde for")
    val idHusstandsbarn: Long,
    @Schema(type = "Long", description = "Id til perioden som skal oppdateres")
    val idPeriode: Long? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    @Schema(required = true)
    val bostatus: Bostatuskode,
)

data class Husstandsbarnperiode(
    val idHusstandsbarn: Long,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val bostatus: Bostatuskode,
)

data class OpprettHusstandsstandsmedlem(
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
