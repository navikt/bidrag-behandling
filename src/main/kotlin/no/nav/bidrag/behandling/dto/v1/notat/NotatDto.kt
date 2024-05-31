package no.nav.bidrag.behandling.dto.v1.notat

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.domene.util.visningsnavnIntern
import no.nav.bidrag.domene.util.visningsnavnMedÅrstall
import no.nav.bidrag.transport.behandling.felles.grunnlag.DelberegningSumInntekt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale

data class NotatDto(
    val saksnummer: String,
    val saksbehandlerNavn: String?,
    val virkningstidspunkt: Virkningstidspunkt,
    val boforhold: Boforhold,
    val roller: List<PersonNotatDto>,
    val inntekter: Inntekter,
    val vedtak: Vedtak,
)

data class Virkningstidspunkt(
    val søknadstype: String?,
    val vedtakstype: Vedtakstype?,
    val søktAv: SøktAvType?,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattDato: LocalDate?,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM")
    val søktFraDato: YearMonth?,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate?,
    @Schema(name = "årsak", enumAsRef = true)
    val årsak: VirkningstidspunktÅrsakstype?,
    val avslag: Resultatkode?,
    val notat: Notat,
) {
    @get:Schema(name = "årsakVisningsnavn")
    val årsakVisningsnavn get() = årsak?.visningsnavn?.intern

    @get:Schema(name = "avslagVisningsnavn")
    val avslagVisningsnavn
        get() = vedtakstype?.let { avslag?.visningsnavnIntern(vedtakstype) } ?: avslag?.visningsnavn?.intern
}

data class Notat(
    val medIVedtaket: String?,
    val intern: String?,
)

data class Boforhold(
    val barn: List<BoforholdBarn> = emptyList(),
    val sivilstand: SivilstandNotat,
    val notat: Notat,
)

data class SivilstandNotat(
    val opplysningerFraFolkeregisteret: List<OpplysningerFraFolkeregisteret<SivilstandskodePDL>> =
        emptyList(),
    val opplysningerBruktTilBeregning: List<OpplysningerBruktTilBeregning<Sivilstandskode>> =
        emptyList(),
)

data class BoforholdBarn(
    val gjelder: PersonNotatDto,
    val medIBehandling: Boolean,
    val kilde: Kilde,
    val opplysningerFraFolkeregisteret: List<OpplysningerFraFolkeregisteret<Bostatuskode>> =
        emptyList(),
    val opplysningerBruktTilBeregning: List<OpplysningerBruktTilBeregning<Bostatuskode>> =
        emptyList(),
)

data class OpplysningerFraFolkeregisteret<T>(
    val periode: ÅrMånedsperiode,
    val status: T?,
) {
    val statusVisningsnavn get() = toVisningsnavn(status)
}

data class OpplysningerBruktTilBeregning<T>(
    val periode: ÅrMånedsperiode,
    val status: T,
    val kilde: Kilde,
) {
    val statusVisningsnavn get() = toVisningsnavn(status)
}

private fun <T> toVisningsnavn(value: T): String? {
    return when (val enum = value) {
        is Bostatuskode -> enum.visningsnavn.intern
        is Inntektsrapportering -> enum.visningsnavn.intern
        is Resultatkode -> enum.visningsnavn.intern
        is Sivilstandskode -> enum.visningsnavn.intern
        is SivilstandskodePDL ->
            enum.name.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

        is Kilde -> enum.name.lowercase().replaceFirstChar { it.uppercase() }
        is VirkningstidspunktÅrsakstype -> enum.visningsnavn.intern
        is SøktAvType ->
            enum.name.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

        else -> null
    }
}

data class PersonNotatDto(
    val rolle: Rolletype?,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val ident: Personident?,
)

data class Inntekter(
    val inntekterPerRolle: List<InntekterPerRolle>,
    val notat: Notat,
)

data class InntekterPerRolle(
    val gjelder: PersonNotatDto,
    val arbeidsforhold: List<Arbeidsforhold> = emptyList(),
    @Schema(name = "årsinntekter")
    val årsinntekter: List<NotatInntektDto> = emptyList(),
    val barnetillegg: List<NotatInntektDto> = emptyList(),
    val utvidetBarnetrygd: List<NotatInntektDto> = emptyList(),
    val småbarnstillegg: List<NotatInntektDto> = emptyList(),
    val kontantstøtte: List<NotatInntektDto> = emptyList(),
    val beregnetInntekter: List<NotatBeregnetInntektDto> = emptyList(),
)

data class NotatBeregnetInntektDto(
    val gjelderBarn: PersonNotatDto,
    val summertInntektListe: List<DelberegningSumInntekt>,
)

data class Arbeidsforhold(
    val periode: ÅrMånedsperiode,
    val arbeidsgiver: String,
    val stillingProsent: String?,
    val lønnsendringDato: LocalDate?,
)

data class NotatInntektDto(
    val periode: ÅrMånedsperiode?,
    val opprinneligPeriode: ÅrMånedsperiode?,
    val beløp: BigDecimal,
    val kilde: Kilde = Kilde.OFFENTLIG,
    val type: Inntektsrapportering,
    val medIBeregning: Boolean = false,
    val gjelderBarn: PersonNotatDto?,
    val inntektsposter: List<NotatInntektspostDto> = emptyList(),
) {
    val visningsnavn
        get() =
            type.visningsnavnMedÅrstall(
                opprinneligPeriode?.fom?.year ?: periode?.fom?.year,
                opprinneligPeriode,
            )
}

data class NotatInntektspostDto(
    val kode: String?,
    val inntektstype: Inntektstype?,
    val beløp: BigDecimal,
) {
    val visningsnavn get() = inntektstype?.visningsnavn?.intern ?: kode?.let { finnVisningsnavn(it) }
}

data class Vedtak(
    val erFattet: Boolean,
    val fattetAvSaksbehandler: String?,
    val fattetTidspunkt: LocalDateTime?,
    val resultat: List<NotatResultatBeregningBarnDto>,
)

data class NotatResultatBeregningBarnDto(
    val barn: PersonNotatDto,
    val perioder: List<NotatResultatPeriodeDto>,
) {
    data class NotatResultatPeriodeDto(
        val periode: ÅrMånedsperiode,
        val beløp: BigDecimal,
        val resultatKode: Resultatkode,
        val regel: String,
        val sivilstand: Sivilstandskode?,
        val inntekt: BigDecimal,
        val vedtakstype: Vedtakstype?,
        val antallBarnIHusstanden: Int,
    ) {
        val resultatKodeVisningsnavn
            get() = vedtakstype?.let { resultatKode.visningsnavnIntern(it) } ?: resultatKode.visningsnavn.intern
        val sivilstandVisningsnavn get() = sivilstand?.visningsnavn?.intern
    }
}
