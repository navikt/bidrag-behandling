package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagNotater
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSøknad
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagVirkningsttidspunkt
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import java.time.YearMonth

data class StønadsendringPeriode(
    val barn: Rolle,
    val perioder: List<OpprettPeriodeRequestDto>,
    val grunnlag: Set<GrunnlagDto>,
)

fun ResultatForskuddsberegningBarn.byggStønadsendringerForVedtak(behandling: Behandling): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == barn.ident?.verdi }
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
    val personobjekter = tilPersonobjekter(søknadsbarnRolle)
    val søknadsbarn = søknadsbarnRolle.tilGrunnlagPerson()
    val bostatusBarn = tilGrunnlagBostatus(personobjekter)

    val inntekter = tilGrunnlagInntekt(personobjekter, søknadsbarn, false)
    val sivilstandBm =
        tilGrunnlagSivilstand(
            personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
        )
    return BeregnGrunnlag(
        periode =
            ÅrMånedsperiode(
                virkningstidspunkt!!,
                YearMonth.now().plusMonths(1).atDay(1),
            ),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe =
            (personobjekter + bostatusBarn + inntekter + sivilstandBm).toList(),
    )
}

fun Collection<GrunnlagDto>.husstandsmedlemmer() = filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }

fun Behandling.byggGrunnlagForVedtak(): Set<GrunnlagDto> {
    val personobjekter = tilPersonobjekter()
    val bostatus = tilGrunnlagBostatus(personobjekter)
    val personobjekterMedHusstandsmedlemmer =
        (personobjekter + bostatus.husstandsmedlemmer()).toMutableSet()
    val innhentetGrunnlagListe = byggInnhentetGrunnlag(personobjekterMedHusstandsmedlemmer)
    // TODO: Er dette for BP i bidrag?
    val sivilstand =
        tilGrunnlagSivilstand(
            personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
        )
    val inntekter = tilGrunnlagInntekt(personobjekter)
    return (personobjekter + bostatus + inntekter + sivilstand + innhentetGrunnlagListe).toSet()
}

fun Behandling.byggGrunnlagForStønad(): Set<GrunnlagDto> {
    return (byggGrunnlagNotater() + byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSøknad()).toSet()
}

fun Behandling.byggGrunnlagForStønadAvslag(): Set<GrunnlagDto> {
    return (byggGrunnlagNotater() + byggGrunnlagSøknad()).toSet()
}
