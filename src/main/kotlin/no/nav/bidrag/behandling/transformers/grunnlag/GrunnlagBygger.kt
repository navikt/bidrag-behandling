package no.nav.bidrag.behandling.transformers.grunnlag

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagNotater
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSærbidragKategori
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagSøknad
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagUtgiftDirekteBetalt
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagUtgiftsposter
import no.nav.bidrag.behandling.transformers.vedtak.byggGrunnlagVirkningsttidspunkt
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import java.time.LocalDate
import java.time.YearMonth

fun finnBeregnTilDato(virkningstidspunkt: LocalDate) =
    maxOf(YearMonth.now().plusMonths(1).atDay(1), virkningstidspunkt!!.plusMonths(1).withDayOfMonth(1))

data class StønadsendringPeriode(
    val barn: Rolle,
    val perioder: List<OpprettPeriodeRequestDto>,
    val grunnlag: Set<GrunnlagDto>,
)

fun Collection<GrunnlagDto>.husstandsmedlemmer() = filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }

fun Behandling.byggGrunnlagForBeregning(søknadsbarnRolle: Rolle): BeregnGrunnlag {
    val personobjekter = tilPersonobjekter(søknadsbarnRolle)
    val søknadsbarn = søknadsbarnRolle.tilGrunnlagPerson()
    val bostatusBarn = tilGrunnlagBostatus(personobjekter)
    val inntekter = tilGrunnlagInntekt(personobjekter, søknadsbarn, false)
    val grunnlagsliste = (personobjekter + bostatusBarn + inntekter).toMutableSet()

    when (tilType()) {
        TypeBehandling.FORSKUDD ->
            grunnlagsliste.addAll(
                tilGrunnlagSivilstand(
                    personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                ),
            )

        TypeBehandling.SÆRBIDRAG ->
            grunnlagsliste.add(tilGrunnlagUtgift())

        else -> {}
    }
    val beregnFraDato = virkningstidspunkt ?: vedtakmappingFeilet("Virkningstidspunkt må settes for beregning")
    val beregningTilDato = finnBeregnTilDato(virkningstidspunkt!!)
    return BeregnGrunnlag(
        periode =
            ÅrMånedsperiode(
                beregnFraDato,
                beregningTilDato,
            ),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe = grunnlagsliste.toList(),
    )
}

fun Behandling.byggGrunnlagForVedtak(): Set<GrunnlagDto> {
    val personobjekter = tilPersonobjekter()
    val bostatus = tilGrunnlagBostatus(personobjekter)
    val personobjekterMedHusstandsmedlemmer =
        (personobjekter + bostatus.husstandsmedlemmer()).toMutableSet()
    val innhentetGrunnlagListe = byggInnhentetGrunnlag(personobjekterMedHusstandsmedlemmer)
    val inntekter = tilGrunnlagInntekt(personobjekter)

    val grunnlagListe = (personobjekter + bostatus + inntekter + innhentetGrunnlagListe).toMutableSet()
    when (tilType()) {
        TypeBehandling.FORSKUDD ->
            grunnlagListe.addAll(
                tilGrunnlagSivilstand(
                    personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                ),
            )

        else -> grunnlagListe.addAll(byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt())
    }
    return grunnlagListe.toSet()
}

fun Behandling.byggGrunnlagGenerelt(): Set<GrunnlagDto> {
    val grunnlagListe = (byggGrunnlagNotater() + byggGrunnlagSøknad()).toMutableSet()
    when (tilType()) {
        TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
        TypeBehandling.SÆRBIDRAG ->
            grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())

        else -> {}
    }
    return grunnlagListe
}

fun Behandling.byggGrunnlagGenereltAvslag(): Set<GrunnlagDto> {
    val grunnlagListe = (byggGrunnlagNotater() + byggGrunnlagSøknad()).toMutableSet()
    when (tilType()) {
        TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
        TypeBehandling.SÆRBIDRAG -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())

        else -> {}
    }
    return grunnlagListe
}
