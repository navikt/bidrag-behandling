package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.transformers.grunnlag.finnFødselsdato
import no.nav.bidrag.boforhold.dto.BoforholdRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.time.LocalDate

fun Set<RelatertPersonGrunnlagDto>.tilBoforholdRequest(virkningsdato: LocalDate) = this.toList().tilBoforholdRequest(virkningsdato)

fun List<RelatertPersonGrunnlagDto>.tilBoforholdRequest(virkningsdato: LocalDate) =
    this.filter { it.erBarnAvBmBp }.map {
        BoforholdRequest(
            bostatusListe =
                when (it.borISammeHusstandDtoListe.isNotEmpty()) {
                    true ->
                        it.borISammeHusstandDtoListe.tilBostatus(
                            Bostatuskode.MED_FORELDER,
                            Kilde.OFFENTLIG,
                        )

                    false ->
                        listOf(
                            Bostatus(
                                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                                kilde = Kilde.OFFENTLIG,
                                periodeFom = maxOf(it.fødselsdato!!, virkningsdato),
                                periodeTom = null,
                            ),
                        )
                },
            erBarnAvBmBp = it.erBarnAvBmBp,
            fødselsdato = it.fødselsdato!!,
            relatertPersonPersonId = it.relatertPersonPersonId,
        )
    }

fun Set<Husstandsbarnperiode>.tilBoforholdRequest(husstandsbarn: Husstandsbarn): BoforholdRequest {
    val bostatus = this.map { it.tilBostatus() }
    return BoforholdRequest(
        bostatusListe = bostatus.sortedBy { it.periodeFom },
        erBarnAvBmBp = true,
        fødselsdato = husstandsbarn.fødselsdato,
        relatertPersonPersonId = husstandsbarn.ident,
    )
}

fun List<Bostatus>.tilBostatusRequest(husstandsbarn: Husstandsbarn) =
    BoforholdRequest(
        relatertPersonPersonId = husstandsbarn.ident,
        fødselsdato = husstandsbarn.fødselsdato,
        erBarnAvBmBp = true,
        bostatusListe = this,
    )

fun Husstandsbarnperiode.tilBostatus() =
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
                kilde = Kilde.OFFENTLIG,
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

fun List<no.nav.bidrag.sivilstand.response.SivilstandV1>.tilSivilstand(behandling: Behandling): List<Sivilstand> =
    this.map {
        Sivilstand(
            behandling = behandling,
            kilde = Kilde.OFFENTLIG,
            datoFom = it.periodeFom,
            datoTom = it.periodeTom,
            sivilstand = it.sivilstandskode,
        )
    }

fun SivilstandBeregnet.tilSivilstand(behandling: Behandling): List<Sivilstand> =
    this.sivilstandListe.map {
        Sivilstand(
            behandling = behandling,
            kilde = Kilde.OFFENTLIG,
            datoFom = it.periodeFom,
            datoTom = it.periodeTom,
            sivilstand = it.sivilstandskode,
        )
    }

fun Set<Sivilstand>.tilSivilstandGrunnlagDto() =
    this.map {
        SivilstandGrunnlagDto(
            gyldigFom = it.datoFom,
            type = it.sivilstand.tilSivilstandskodePDL(),
            bekreftelsesdato = null,
            personId = null,
            master = null,
            historisk = null,
            registrert = null,
        )
    }

fun Sivilstandskode.tilSivilstandskodePDL() =
    when (this) {
        Sivilstandskode.BOR_ALENE_MED_BARN -> SivilstandskodePDL.SKILT
        Sivilstandskode.GIFT_SAMBOER -> SivilstandskodePDL.GIFT
        Sivilstandskode.SAMBOER -> SivilstandskodePDL.GIFT
        Sivilstandskode.ENSLIG -> SivilstandskodePDL.SKILT
        Sivilstandskode.UKJENT -> SivilstandskodePDL.UOPPGITT
    }
