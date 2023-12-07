package no.nav.bidrag.behandling.dto.notat

import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class NotatDto(
    val saksnummer: String,
    val saksbehandlerNavn: String?,
    val virkningstidspunkt: Virkningstidspunkt,
    val boforhold: Boforhold,
    val parterISøknad: List<ParterISøknad>,
    val inntekter: Inntekter,
    val vedtak: List<Vedtak>,
)

data class Virkningstidspunkt(
    val søknadstype: String?,
    val søktAv: SøktAvType?,
    val mottattDato: YearMonth?,
    val søktFraDato: YearMonth?,
    val virkningstidspunkt: LocalDate?,
    val notat: Notat,
)

data class Notat(
    val medIVedtaket: String?,
    val intern: String?,
)

data class Boforhold(
    val barn: List<BoforholdBarn> = emptyList(),
    val sivilstand: List<SivilstandPeriode> = emptyList(),
    val notat: Notat,
)

data class BoforholdBarn(
    val navn: String,
    val fødselsdato: LocalDate?,
    val opplysningerFraFolkeregisteret: List<OpplysningerFraFolkeregisteret> = emptyList(),
    val opplysningerBruktTilBeregning: List<OpplysningerBruktTilBeregning> = emptyList(),
)

data class OpplysningerFraFolkeregisteret(
    val periode: ÅrMånedsperiode,
    val status: String?,
)

data class OpplysningerBruktTilBeregning(
    val periode: ÅrMånedsperiode,
    val status: Bostatuskode,
    val kilde: String,
)

data class SivilstandPeriode(
    val periode: ÅrMånedsperiode,
    val status: Sivilstandskode?,
    val kilde: String
)

data class ParterISøknad(
    val rolle: Rolletype,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val personident: Personident?,
)

data class Inntekter(
    val inntekterPerRolle: List<InntekterPerRolle>,
    val notat: Notat,
)

data class InntekterPerRolle(
    val rolle: Rolletype,
    val arbeidsforhold: List<Arbeidsforhold>,
    val inntekterSomLeggesTilGrunn: List<InntekterSomLeggesTilGrunn>,
    val barnetillegg: List<Barnetillegg>,
    val utvidetBarnetrygd: List<UtvidetBarnetrygd>,
)

data class Arbeidsforhold(
    val periode: ÅrMånedsperiode,
    val arbeidsgiver: String,
    val stillingProsent: String,
    val lønnsendringDato: LocalDate,
)

data class InntekterSomLeggesTilGrunn(
    val inntektType: Inntektsrapportering?,
    val beskrivelse: String?,
    val periode: ÅrMånedsperiode?,
    val beløp: BigDecimal,
)

data class Barnetillegg(
    val periode: ÅrMånedsperiode,
    val beløp: BigDecimal,
)

data class UtvidetBarnetrygd(
    val periode: ÅrMånedsperiode,
    val beløp: BigDecimal,
)

data class Vedtak(
    val navn: String,
    val fødselsdato: LocalDate,
    val resultat: List<Resultat>,
)

data class Resultat(
    val type: String,
    val periode: ÅrMånedsperiode,
    val inntekt: BigDecimal,
    val sivilstand: String,
    val antallBarn: Int,
    val resultat: String,
)
