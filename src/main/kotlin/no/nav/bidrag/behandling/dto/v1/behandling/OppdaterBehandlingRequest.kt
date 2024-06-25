package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsmedlemDtoV2
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import java.time.LocalDate

data class OppdatereVirkningstidspunkt(
    @Schema(
        name = "årsak",
        description =
            "Oppdater årsak. Hvis verdien er satt til null så vil det ikke bli gjort noe endringer. " +
                "Hvis verdien er satt så vil årsak settes til samme verdi fra forespørsel og avslag settes til null",
        enumAsRef = true,
    )
    @JsonSetter(nulls = Nulls.SKIP)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(
        description =
            "Oppdater avslag. Hvis verdien er satt til null så vil det ikke bli gjort noe endringer. " +
                "Hvis verdien er satt så vil avslag settes til samme verdi fra forespørsel og årsak settes til null",
        enumAsRef = true,
    )
    val avslag: Resultatkode? = null,
    @Schema(
        type = "string",
        format = "date",
        example = "2025-01-25",
        description =
            "Oppdater virkningsdato. Hvis verdien er satt til null vil det ikke bli gjort noe endringer",
    )
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonSetter(nulls = Nulls.SKIP)
    val virkningstidspunkt: LocalDate? = null,
    val notat: OppdaterNotat? = null,
)

@Deprecated(
    "Erstattes av OppdatereBoforholdRequestV2 - Oppdatering av boforhold gjøres via eget " +
        "endepunkt (/behandling/{behandlingsid}/boforhold)",
)
@Schema(
    description = """
For `husstandsmedlem` og `sivilstand`
* Hvis feltet er null eller ikke satt vil det ikke bli gjort noe endringer. 
* Hvis feltet er tom liste vil alt bli slettet
* Innholdet i listen vil erstatte alt som er lagret. Det er derfor ikke mulig å endre på deler av informasjon i listene.
""",
)
data class OppdaterBoforholdRequest(
    val husstandsmedlem: Set<HusstandsmedlemDtoV2>? = null,
    val sivilstand: Set<SivilstandDto>? = null,
    val notat: OppdaterNotat? = null,
)

data class OppdaterNotat(
    val kunINotat: String? = null,
)
