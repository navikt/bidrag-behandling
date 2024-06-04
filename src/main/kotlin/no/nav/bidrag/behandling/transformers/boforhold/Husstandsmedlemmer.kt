package no.nav.bidrag.behandling.transformers.boforhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto

private val log = KotlinLogging.logger {}

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdbBarnRequest(behandling: Behandling) = this.toList().tilBoforholdbBarnRequest(behandling)

fun List<RelatertPersonGrunnlagDto>.tilBoforholdbBarnRequest(behandling: Behandling) =

    this.filter { it.erBarnAvBmBp }.map { g ->
        BoforholdBarnRequest(
            innhentedeOffentligeOpplysninger =
                when (g.borISammeHusstandDtoListe.isNotEmpty()) {
                    true ->
                        g.borISammeHusstandDtoListe.tilBostatus(
                            Bostatuskode.MED_FORELDER,
                            Kilde.OFFENTLIG,
                        )

                    false ->
                        listOf(
                            Bostatus(
                                bostatusKode = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = maxOf(g.fødselsdato!!, behandling.virkningstidspunktEllerSøktFomDato),
                                periodeTom = null,
                            ),
                        )
                },
            erBarnAvBmBp =
                if (behandling.husstandsbarn.find {
                        it.ident != null && it.ident == g.relatertPersonPersonId
                    } != null
                ) {
                    true
                } else {
                    g.erBarnAvBmBp
                },
            fødselsdato = g.fødselsdato!!,
            relatertPersonPersonId = g.relatertPersonPersonId,
            behandledeBostatusopplysninger = emptyList(),
            endreBostatus = null,
        )
    }

fun Husstandsbarn.tilBoforholdbBarnRequest(
    endreBostatus: EndreBostatus? = null,
    erBarnAvBmBp: Boolean = true,
): BoforholdBarnRequest {
    return BoforholdBarnRequest(
        relatertPersonPersonId = ident,
        fødselsdato = fødselsdato,
        erBarnAvBmBp = erBarnAvBmBp,
        innhentedeOffentligeOpplysninger =
            henteOffentligePerioder().map { it.tilBostatus() }.sortedBy { it.periodeFom },
        behandledeBostatusopplysninger = perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
        endreBostatus = endreBostatus,
    )
}

fun Husstandsbarn.henteOffentligePerioder(): Set<Husstandsbarnperiode> =
    hentSisteBearbeidetBoforhold()?.tilPerioder(this) ?: if (kilde == Kilde.OFFENTLIG) {
        log.warn {
            "Fant ikke originale bearbeidet perioder for offentlig husstandsmedlem $id i behandling ${behandling.id}. Lagt til initiell periode "
        }
        setOf(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    } else {
        setOf()
    }

fun Husstandsbarnperiode.tilBostatus() =
    Bostatus(
        bostatusKode = this.bostatus,
        kilde = this.kilde,
        periodeFom = this.datoFom,
        periodeTom = this.datoTom,
    )

fun List<BorISammeHusstandDto>.tilBostatus(
    bostatus: Bostatuskode,
    kilde: Kilde,
) = this.map {
    Bostatus(
        bostatusKode = bostatus,
        kilde = kilde,
        periodeFom = it.periodeFra,
        periodeTom = it.periodeTil,
    )
}

fun List<BoforholdResponse>.tilPerioder(husstandsbarn: Husstandsbarn) =
    this.find { it.relatertPersonPersonId == husstandsbarn.ident }?.let {
        map { boforhold ->
            boforhold.tilPeriode(husstandsbarn)
        }.toMutableSet()
    } ?: setOf()

fun BoforholdResponse.tilPeriode(husstandsbarn: Husstandsbarn) =
    Husstandsbarnperiode(
        bostatus = bostatus,
        datoFom = periodeFom,
        datoTom = periodeTom,
        kilde = kilde,
        husstandsbarn = husstandsbarn,
    )

fun List<BoforholdResponse>.tilHusstandsbarn(behandling: Behandling): Set<Husstandsbarn> {
    return this.groupBy { it.relatertPersonPersonId }.map {
        val fødselsdatoFraRespons = it.value.first().fødselsdato
        val husstandsbarn =
            Husstandsbarn(
                behandling = behandling,
                kilde = it.value.first().kilde,
                ident = it.key,
                fødselsdato = finnFødselsdato(it.key, fødselsdatoFraRespons) ?: fødselsdatoFraRespons,
            )
        husstandsbarn.overskriveMedBearbeidaPerioder(it.value)
        husstandsbarn
    }.toSet()
}

fun Husstandsbarn.overskriveMedBearbeidaPerioder(nyePerioder: List<BoforholdResponse>) {
    perioder.clear()
    perioder.addAll(nyePerioder.tilPerioder(this))
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

fun Husstandsbarn.opprettDefaultPeriodeForOffentligHusstandsmedlem() =
    Husstandsbarnperiode(
        husstandsbarn = this,
        datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato),
        datoTom = null,
        bostatus = Bostatuskode.IKKE_MED_FORELDER,
        kilde = Kilde.OFFENTLIG,
    )
