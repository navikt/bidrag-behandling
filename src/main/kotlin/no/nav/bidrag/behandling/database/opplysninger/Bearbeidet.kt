package no.nav.bidrag.behandling.database.opplysninger

import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.time.LocalDate
import java.time.LocalDateTime

data class BoforholdBearbeidet(
    val husstand: List<BoforholdHusstandBearbeidet> = emptyList(),
    val sivilstand: List<SivilstandBearbeidet> = emptyList(),
)

data class SivilstandBearbeidet(
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
    val sivilstand: Sivilstandskode,
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
    val barnetillegg: List<BarnetilleggDto> = emptyList(),
    val arbeidsforhold: List<ArbeidsforholdGrunnlagDto> = emptyList(),
)

data class InntektBearbeidet(
    val ident: String,
    val versjon: String?,
    val summertAarsinntektListe: List<SummertÅrsinntekt>,
)
