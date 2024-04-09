package no.nav.bidrag.behandling.dto.v1.behandling

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v1.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.v1.inntekt.KontantstøtteDto
import no.nav.bidrag.behandling.dto.v1.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import java.time.LocalDate

data class OppdaterBehandlingRequest(
    val virkningstidspunkt: OppdaterVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequest? = null,
)

data class OppdaterVirkningstidspunkt(
    @Schema(
        name = "årsak",
        description =
            "Oppdater årsak. Hvis verdien er satt til null vil årsak bli slettet. " +
                "Hvis verdien er satt til tom verdi eller ikke er satt vil det ikke bli gjort noe endringer",
        enumAsRef = true,
    )
    @JsonSetter(nulls = Nulls.SKIP)
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @Schema(
        enumAsRef = true,
    )
    val avslag: Resultatkode? = null,
    @Schema(
        type = "string",
        format = "date",
        example = "2025-01-25",
        description =
            "Oppdater virkningsdato. Hvis verdien er satt til null vil virkningsdato bli slettet. " +
                "Hvis verdien er satt til tom verdi eller ikke er satt vil det ikke bli gjort noe endringer",
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
For `husstandsbarn` og `sivilstand`
* Hvis feltet er null eller ikke satt vil det ikke bli gjort noe endringer. 
* Hvis feltet er tom liste vil alt bli slettet
* Innholdet i listen vil erstatte alt som er lagret. Det er derfor ikke mulig å endre på deler av informasjon i listene.
""",
)
data class OppdaterBoforholdRequest(
    val husstandsbarn: Set<HusstandsbarnDto>? = null,
    val sivilstand: Set<SivilstandDto>? = null,
    val notat: OppdaterNotat? = null,
)

@Schema(
    description = """
For `inntekter`, `kontantstøtte`, `småbarnstillegg`, `barnetillegg`, `utvidetBarnetrygd`
* Hvis feltet er null eller ikke satt vil det ikke bli gjort noe endringer. 
* Hvis feltet er tom liste vil alt bli slettet
* Innholdet i listen vil erstatte alt som er lagret. Det er derfor ikke mulig å endre på deler av informasjon i listene.
""",
)
data class OppdatereInntekterRequest(
    val inntekter: Set<InntektDto>? = null,
    val kontantstøtte: Set<KontantstøtteDto>? = null,
    val småbarnstillegg: Set<InntektDto>? = null,
    val barnetillegg: Set<BarnetilleggDto>? = null,
    val utvidetbarnetrygd: Set<UtvidetBarnetrygdDto>? = null,
    val notat: OppdaterNotat? = null,
)

data class OppdaterNotat(
    val kunINotat: String? = null,
    val medIVedtaket: String? = null,
)
