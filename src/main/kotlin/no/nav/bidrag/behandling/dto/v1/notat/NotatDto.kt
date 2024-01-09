package no.nav.bidrag.behandling.dto.v1.notat

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
    val virkningstidspunkt: no.nav.bidrag.behandling.dto.v1.notat.Virkningstidspunkt,
    val boforhold: no.nav.bidrag.behandling.dto.v1.notat.Boforhold,
    val parterISøknad: List<no.nav.bidrag.behandling.dto.v1.notat.ParterISøknad>,
    val inntekter: no.nav.bidrag.behandling.dto.v1.notat.Inntekter,
    val vedtak: List<no.nav.bidrag.behandling.dto.v1.notat.Vedtak>,
)

data class Virkningstidspunkt(
    val søknadstype: String?,
    val søktAv: SøktAvType?,
    val mottattDato: YearMonth?,
    val søktFraDato: YearMonth?,
    val virkningstidspunkt: LocalDate?,
    val notat: no.nav.bidrag.behandling.dto.v1.notat.Notat,
)

data class Notat(
    val medIVedtaket: String?,
    val intern: String?,
)

data class Boforhold(
    val barn: List<no.nav.bidrag.behandling.dto.v1.notat.BoforholdBarn> = emptyList(),
    val sivilstand: no.nav.bidrag.behandling.dto.v1.notat.SivilstandNotat,
    val notat: no.nav.bidrag.behandling.dto.v1.notat.Notat,
)

data class SivilstandNotat(
    val opplysningerFraFolkeregisteret: List<no.nav.bidrag.behandling.dto.v1.notat.OpplysningerFraFolkeregisteret<Sivilstandskode>> =
        emptyList(),
    val opplysningerBruktTilBeregning: List<no.nav.bidrag.behandling.dto.v1.notat.OpplysningerBruktTilBeregning<Sivilstandskode>> =
        emptyList(),
)

data class BoforholdBarn(
    val navn: String,
    val fødselsdato: LocalDate?,
    val opplysningerFraFolkeregisteret: List<no.nav.bidrag.behandling.dto.v1.notat.OpplysningerFraFolkeregisteret<Bostatuskode>> =
        emptyList(),
    val opplysningerBruktTilBeregning: List<no.nav.bidrag.behandling.dto.v1.notat.OpplysningerBruktTilBeregning<Bostatuskode>> =
        emptyList(),
)

data class OpplysningerFraFolkeregisteret<T>(
    val periode: ÅrMånedsperiode,
    val status: T?,
)

data class OpplysningerBruktTilBeregning<T>(
    val periode: ÅrMånedsperiode,
    val status: T,
    val kilde: String,
)

data class ParterISøknad(
    val rolle: Rolletype,
    val navn: String?,
    val fødselsdato: LocalDate?,
    val personident: Personident?,
)

data class Inntekter(
    val inntekterPerRolle: List<no.nav.bidrag.behandling.dto.v1.notat.InntekterPerRolle>,
    val notat: no.nav.bidrag.behandling.dto.v1.notat.Notat,
)

data class InntekterPerRolle(
    val rolle: Rolletype,
    val arbeidsforhold: List<no.nav.bidrag.behandling.dto.v1.notat.Arbeidsforhold>,
    val inntekterSomLeggesTilGrunn: List<no.nav.bidrag.behandling.dto.v1.notat.InntekterSomLeggesTilGrunn>,
    val barnetillegg: List<no.nav.bidrag.behandling.dto.v1.notat.Barnetillegg>,
    val utvidetBarnetrygd: List<no.nav.bidrag.behandling.dto.v1.notat.UtvidetBarnetrygd>,
)

data class Arbeidsforhold(
    val periode: ÅrMånedsperiode,
    val arbeidsgiver: String,
    val stillingProsent: String?,
    val lønnsendringDato: LocalDate?,
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
    val resultat: List<no.nav.bidrag.behandling.dto.v1.notat.Resultat>,
)

data class Resultat(
    val type: String,
    val periode: ÅrMånedsperiode,
    val inntekt: BigDecimal,
    val sivilstand: String,
    val antallBarn: Int,
    val resultat: String,
)
