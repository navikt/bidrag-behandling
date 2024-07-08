package no.nav.bidrag.behandling.transformers.boforhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.hentSisteBearbeidetBoforhold
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.boforhold.dto.BoforholdBarnRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.boforhold.dto.EndreBostatus
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto

private val log = KotlinLogging.logger {}

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(behandling: Behandling) = this.toList().tilBoforholdBarnRequest(behandling)

fun List<RelatertPersonGrunnlagDto>.tilBoforholdBarnRequest(behandling: Behandling): List<BoforholdBarnRequest> {
    val barnAvBmBpManglerFødselsdato = this.filter { it.erBarn }.filter { it.fødselsdato == null }
    if (barnAvBmBpManglerFødselsdato.isNotEmpty()) {
        secureLogger.warn {
            "Husstandsmedlem som er barn av BM eller BP (personident forelder: ${barnAvBmBpManglerFødselsdato.first().partPersonId}) mangler fødselsdato."
        }
    }

    return this.filter { it.erBarn }.filter { it.fødselsdato != null }.map { g ->
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
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = maxOf(g.fødselsdato!!, behandling.virkningstidspunktEllerSøktFomDato),
                                periodeTom = null,
                            ),
                        )
                },
            erBarnAvBmBp =
                if (behandling.husstandsmedlem.find {
                        it.ident != null && it.ident == g.gjelderPersonId
                    } != null
                ) {
                    true
                } else {
                    g.erBarn
                },
            fødselsdato = g.fødselsdato!!,
            relatertPersonPersonId = g.gjelderPersonId,
            behandledeBostatusopplysninger = emptyList(),
            endreBostatus = null,
        )
    }
}

fun Husstandsmedlem.tilBoforholdBarnRequest(
    endreBostatus: EndreBostatus? = null,
    erBarnAvBmBp: Boolean = true,
): BoforholdBarnRequest =
    BoforholdBarnRequest(
        relatertPersonPersonId = ident,
        fødselsdato = fødselsdato,
        erBarnAvBmBp = erBarnAvBmBp,
        innhentedeOffentligeOpplysninger =
            henteOffentligePerioder().map { it.tilBostatus() }.sortedBy { it.periodeFom },
        behandledeBostatusopplysninger = perioder.map { it.tilBostatus() }.sortedBy { it.periodeFom },
        endreBostatus = endreBostatus,
    )

fun Husstandsmedlem.henteOffentligePerioder(): Set<Bostatusperiode> =
    hentSisteBearbeidetBoforhold()?.tilPerioder(this) ?: if (kilde == Kilde.OFFENTLIG) {
        log.warn {
            "Fant ikke originale bearbeidet perioder for offentlig husstandsmedlem $id i behandling ${behandling.id}. Lagt til initiell periode "
        }
        setOf(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    } else {
        setOf()
    }

fun Bostatusperiode.tilBostatus() =
    Bostatus(
        bostatus = this.bostatus,
        kilde = this.kilde,
        periodeFom = this.datoFom,
        periodeTom = this.datoTom,
    )

fun List<BorISammeHusstandDto>.tilBostatus(
    bostatus: Bostatuskode,
    kilde: Kilde,
) = this.map {
    Bostatus(
        bostatus = bostatus,
        kilde = kilde,
        periodeFom = it.periodeFra,
        periodeTom = it.periodeTil,
    )
}

fun List<BoforholdResponse>.tilPerioder(husstandsmedlem: Husstandsmedlem) =
    this.find { it.relatertPersonPersonId == husstandsmedlem.ident }?.let {
        map { boforhold ->
            boforhold.tilPeriode(husstandsmedlem)
        }.toMutableSet()
    } ?: setOf()

fun BoforholdResponse.tilPeriode(husstandsmedlem: Husstandsmedlem) =
    Bostatusperiode(
        bostatus = bostatus,
        datoFom = periodeFom,
        datoTom = periodeTom,
        kilde = kilde,
        husstandsmedlem = husstandsmedlem,
    )

fun List<BoforholdResponse>.tilHusstandsmedlem(behandling: Behandling): Set<Husstandsmedlem> =
    this
        .groupBy { it.relatertPersonPersonId }
        .map {
            val fødselsdatoFraRespons = it.value.first().fødselsdato
            val husstandsmedlem =
                Husstandsmedlem(
                    behandling = behandling,
                    kilde = it.value.first().kilde,
                    ident = it.key,
                    fødselsdato = finnFødselsdato(it.key, fødselsdatoFraRespons) ?: fødselsdatoFraRespons,
                )
            husstandsmedlem.overskriveMedBearbeidaPerioder(it.value)
            husstandsmedlem
        }.toSet()

fun Husstandsmedlem.overskriveMedBearbeidaPerioder(nyePerioder: List<BoforholdResponse>) {
    perioder.clear()
    perioder.addAll(nyePerioder.tilPerioder(this))
    if (perioder.isEmpty()) {
        perioder.add(opprettDefaultPeriodeForOffentligHusstandsmedlem())
    }
}

fun Husstandsmedlem.opprettDefaultPeriodeForOffentligHusstandsmedlem() =
    Bostatusperiode(
        husstandsmedlem = this,
        datoFom = maxOf(behandling.virkningstidspunktEllerSøktFomDato, fødselsdato),
        datoTom = null,
        bostatus = Bostatuskode.IKKE_MED_FORELDER,
        kilde = Kilde.OFFENTLIG,
    )
