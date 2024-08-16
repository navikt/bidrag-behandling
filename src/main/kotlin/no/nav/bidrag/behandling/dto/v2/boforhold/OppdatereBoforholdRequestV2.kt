package no.nav.bidrag.behandling.dto.v2.boforhold

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdValideringsfeil
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereBegrunnelse
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.time.LocalDate
import java.time.YearMonth

@Schema(description = "Oppdaterer husstandsmedlem, sivilstand, eller notat")
data class OppdatereBoforholdRequestV2(
    val oppdaterePeriodeMedAndreVoksneIHusstand: OppdatereAndreVoksneIHusstanden? = null,
    val oppdatereHusstandsmedlem: OppdatereHusstandsmedlem? = null,
    val oppdatereSivilstand: OppdatereSivilstand? = null,
    @Schema(description = "Oppdatere saksbehandlers begrunnelse")
    val oppdatereBegrunnelse: OppdatereBegrunnelse? = null,
    @Schema(description = "Deprekert - Bruk oppdatereBegrunnelse i stedet")
    val oppdatereNotat: OppdatereBegrunnelse? = null,
) {
    // TODO: Fjerne når migrering til oppdatereBegrunnelse er fullført
    val henteOppdatereBegrunnelse = oppdatereBegrunnelse ?: oppdatereNotat
}

data class OppdatereBoforholdResponse(
    @Schema(description = "Oppdaterte perioder med andre voksne i Bps husstand")
    val oppdatertePerioderMedAndreVoksne: Set<BostatusperiodeDto> = emptySet(),
    @Schema(description = "Husstandsmedlem som ble opprettet")
    val oppdatertHusstandsmedlem: HusstandsmedlemDtoV2? = null,
    val egetBarnErEnesteVoksenIHusstanden: Boolean? = null,
    val oppdatertSivilstandshistorikk: Set<SivilstandDto> = emptySet(),
    val begrunnelse: String? = null,
    val valideringsfeil: BoforholdValideringsfeil,
) {
    @Deprecated("Erstattes av oppdatertHusstandsmedlem")
    @Schema(description = "Erstattes av oppdatertHusstandsmedlem", deprecated = true)
    val oppdatertHusstandsbarn: HusstandsmedlemDtoV2? = oppdatertHusstandsmedlem
    @Deprecated("Erstattes av begrunnelse")
    @Schema(description = "Saksbehandlers notat", deprecated = true)
    val oppdatertNotat: OppdatereBegrunnelse? = begrunnelse?.let { OppdatereBegrunnelse(it) }
}

data class OppdatereAndreVoksneIHusstanden(
    @Schema(description = "Oppdatere bor-med-andre-voksne-status på periode")
    val oppdaterePeriode: OppdatereAndreVoksneIHusstandenperiode? = null,
    @Schema(type = "Long", description = "Id til perioden som skal slettes")
    val slettePeriode: Long? = null,
    @Schema(type = "Long", description = "Angi om historikken skal tilbakestilles til siste aktiverte grunnlagsdata")
    val tilbakestilleHistorikk: Boolean = false,
    @Schema(type = "Long", description = "Angi om siste endring skal angres")
    val angreSisteEndring: Boolean = false,
)

data class OppdatereAndreVoksneIHusstandenperiode(
    @Schema(type = "Long", description = "Id til bostatusperioden som skal oppdateres, oppretter ny hvis null")
    val idPeriode: Long? = null,
    @Schema(
        description = "Periode, fra-og-med til-og-med måned. Ignoreres for særbidrag",
        format = "date",
        example = "2025-01",
    )
    val periode: ÅrMånedsperiode = ÅrMånedsperiode(YearMonth.now(), YearMonth.now()),
    @Schema(required = true)
    val borMedAndreVoksne: Boolean = true,
)

data class OppdatereHusstandsmedlem(
    @Schema(description = "Informasjon om husstandsmedlem som skal opprettes")
    val opprettHusstandsmedlem: OpprettHusstandsstandsmedlem? = null,
    val oppdaterPeriode: OppdatereBostatusperiode? = null,
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

data class OppdatereBostatusperiode(
    @Deprecated("Erstattes av idHusstandsmedlem")
    @Schema(type = "Long", description = "Id til husstandsbarnet perioden skal gjelde for", deprecated = true)
    var idHusstandsbarn: Long = 0L,
    @Schema(type = "Long", description = "Id til husstandsmedlemmet perioden skal gjelde for")
    var idHusstandsmedlem: Long = 0L,
    @Schema(type = "Long", description = "Id til perioden som skal oppdateres")
    val idPeriode: Long? = null,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoFom: LocalDate?,
    @Schema(type = "string", format = "date", example = "2025-01-25")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val datoTom: LocalDate?,
    val periode: ÅrMånedsperiode = ÅrMånedsperiode(YearMonth.now(), YearMonth.now()),
    @Schema(required = true)
    val bostatus: Bostatuskode,
) {
    // TODO: Slette når migrering fra idHusstandsbarn til idHusstandsmedlem er fullført
    init {
        idHusstandsbarn =
            if (idHusstandsbarn == 0L && idHusstandsmedlem > 0L) {
                idHusstandsmedlem
            } else {
                idHusstandsbarn
            }

        idHusstandsmedlem =
            if (idHusstandsmedlem == 0L && idHusstandsbarn > 0L) {
                idHusstandsbarn
            } else {
                idHusstandsmedlem
            }
    }
}

data class OpprettHusstandsstandsmedlem(
    val personident: Personident? = null,
    val fødselsdato: LocalDate,
    val navn: String? = null,
)

data class OppdatereSivilstand(
    val nyEllerEndretSivilstandsperiode: Sivilstandsperiode? = null,
    val sletteSivilstandsperiode: Long? = null,
    @Schema(type = "Long", description = "Tilbakestiller til historikk fra offentlige registre")
    val tilbakestilleHistorikk: Boolean = false,
    @Schema(type = "Boolean", description = "Settes til true for å angre siste endring")
    val angreSisteEndring: Boolean = false,
)

data class Sivilstandsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate? = null,
    val sivilstand: Sivilstandskode,
    val id: Long? = null,
)
