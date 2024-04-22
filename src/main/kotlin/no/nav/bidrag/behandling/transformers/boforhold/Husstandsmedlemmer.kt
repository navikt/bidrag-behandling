package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.boforhold.dto.BoforholdRequest
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.boforhold.dto.Bostatus
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto

fun List<RelatertPersonGrunnlagDto>.tilBoforholdRequest() =
    this.map {
        BoforholdRequest(
            bostatusListe =
                it.borISammeHusstandDtoListe.tilBostatus(
                    Bostatuskode.MED_FORELDER,
                    Kilde.OFFENTLIG,
                ),
            erBarnAvBmBp = it.erBarnAvBmBp,
            fødselsdato = it.fødselsdato!!,
            relatertPersonPersonId = it.relatertPersonPersonId,
        )
    }

fun Set<Husstandsbarnperiode>.tilBoforholdRequest(husstandsbarn: Husstandsbarn): BoforholdRequest {
    val bostatus = this.map { it.tilBostatus() }
    return BoforholdRequest(
        bostatusListe = bostatus,
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
    kilde: no.nav.bidrag.domene.enums.diverse.Kilde,
) = this.map {
    Bostatus(
        bostatus = bostatus,
        kilde = kilde,
        periodeFom = it.periodeFra,
        periodeTom = it.periodeTil,
    )
}

fun List<BoforholdResponse>.tilHusstandsbarn(
    behandling: Behandling,
    bidragPersonConsumer: BidragPersonConsumer,
): Set<Husstandsbarn> {
    return this.groupBy { it.relatertPersonPersonId }.map {
        val husstandsbarn =
            Husstandsbarn(
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                ident = it.key,
                fødselsdato = bidragPersonConsumer.hentPerson(it.key!!).fødselsdato!!,
            )
        husstandsbarn.perioder =
            it.value.map { boforhold ->
                Husstandsbarnperiode(
                    bostatus = boforhold.bostatus,
                    datoFom = boforhold.periodeFom,
                    datoTom = boforhold.periodeTom,
                    kilde = boforhold.kilde,
                    husstandsbarn = husstandsbarn,
                )
            }.toMutableSet()
        husstandsbarn
    }.toSet()
}
