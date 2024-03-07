package no.nav.bidrag.behandling.database.grunnlag

import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDate
import java.time.LocalDateTime

data class BoforholdBearbeidet(
    val husstand: List<BoforholdHusstandBearbeidet> = emptyList(),
    val sivilstand: List<SivilstandBearbeidet> = emptyList(),
)

data class SivilstandBearbeidet(
    val gyldigFom: LocalDate,
    val type: SivilstandskodePDL,
)

data class BoforholdHusstandBearbeidet(
    val foedselsdato: LocalDate?,
    val ident: String,
    val navn: String?,
    val perioder: List<BoforholdBearbeidetPeriode> = emptyList(),
)

data class BoforholdBearbeidetPeriode(
    val fraDato: LocalDateTime,
    val tilDato: LocalDateTime?,
    val bostatus: Bostatuskode,
)

data class InntektsopplysningerBearbeidet(
    val inntekt: List<InntektBearbeidet> = emptyList(),
    val utvidetbarnetrygd: List<UtvidetBarnetrygdOgSmaabarnstilleggDto> = emptyList(),
    val barnetillegg: List<BarnetilleggGrunnlagDto> = emptyList(),
    val arbeidsforhold: List<ArbeidsforholdGrunnlagDto> = emptyList(),
)

data class InntektBearbeidet(
    val ident: String,
    val versjon: String?,
    val summertAarsinntektListe: List<SummertÅrsinntekt>,
    val summertMånedsinntektListe: List<SummertMånedsinntekt>,
)

data class SummerteInntekter<T>(
    val versjon: String? = null,
    val inntekter: List<T>,
)

data class BearbeidetInntekter<T>(
    val versjon: String,
    val inntekter: List<T>,
)

data class SkattepliktigeInntekter<T, R>(
    val versjon: String? = null,
    val ainntekter: List<T>,
    val skattegrunnlag: List<R>,
)
