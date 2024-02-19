package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagNotater
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSøknad
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagVirkningsttidspunkt
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import java.time.LocalDate

data class StønadsendringPeriode(
    val barn: Rolle,
    val perioder: List<OpprettPeriodeRequestDto>,
    val grunnlag: Set<GrunnlagDto>,
)

fun ResultatForskuddsberegningBarn.byggStønadsendringerForVedtak(behandling: Behandling): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == this.barn.ident?.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = resultat.grunnlagListe.toSet()
    val periodeliste =
        resultat.beregnetForskuddPeriodeListe.map {
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp = it.resultat.belop,
                valutakode = "NOK",
                resultatkode = it.resultat.kode.name,
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste,
        grunnlagListe,
    )
}

fun Behandling.byggGrunnlagForBeregning(søknadsbarnRolle: Rolle): BeregnGrunnlag {
    val personobjekter = tilPersonobjekter()
    val søknadsbarn = søknadsbarnRolle.tilGrunnlagPerson()
    val bostatusBarn = tilGrunnlagBostatus(personobjekter)
    val inntekter = tilGrunnlagInntekt(personobjekter, søknadsbarn)
    val sivilstandBm = tilGrunnlagSivilstand(personobjekter.bidragsmottaker)
    return BeregnGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunkt!!,
                datoTom?.plusDays(1) ?: LocalDate.MAX,
            ),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe =
            (personobjekter + bostatusBarn + inntekter + sivilstandBm).toList(),
    )
}

fun Behandling.byggGrunnlagForVedtak(): Set<GrunnlagDto> {
    val personobjekter = tilPersonobjekter()
    val innhentetGrunnlagListe = byggInnhentetGrunnlag(personobjekter)
    val bostatus = tilGrunnlagBostatus(personobjekter)
    // TODO: Er dette for BP i bidrag?
    val sivilstand = tilGrunnlagSivilstand(personobjekter.bidragsmottaker)
    val inntekter = tilGrunnlagInntekt(personobjekter)
    return (personobjekter + bostatus + inntekter + sivilstand + innhentetGrunnlagListe).toSet()
}

fun Behandling.byggGrunnlagForStønad(): Set<GrunnlagDto> {
    return (byggGrunnlagNotater() + byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSøknad()).toSet()
}
