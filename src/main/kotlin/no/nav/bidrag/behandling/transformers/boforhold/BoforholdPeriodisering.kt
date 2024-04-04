package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.boforhold.response.BoforholdBeregnet
import no.nav.bidrag.boforhold.response.RelatertPerson
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto

fun List<RelatertPersonGrunnlagDto>.tilRelatertPerson() =
    this.map {
        RelatertPerson(
            borISammeHusstandDtoListe = it.borISammeHusstandDtoListe,
            erBarnAvBmBp = it.erBarnAvBmBp,
            fødselsdato = it.fødselsdato,
            relatertPersonPersonId = it.relatertPersonPersonId,
        )
    }

fun List<BoforholdBeregnet>.tilHusstandsbarn(
    behandling: Behandling,
    bidragPersonConsumer: BidragPersonConsumer,
): List<Husstandsbarn> {
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
                    kilde = Kilde.OFFENTLIG,
                    husstandsbarn = husstandsbarn,
                )
            }.toMutableSet()
        husstandsbarn
    }
}

fun List<no.nav.bidrag.sivilstand.response.Sivilstand>.tilSivilstand(behandling: Behandling): List<Sivilstand> =
    this.map {
        Sivilstand(
            behandling = behandling,
            kilde = Kilde.OFFENTLIG,
            datoFom = it.periodeFom,
            datoTom = it.periodeTom,
            sivilstand = it.sivilstandskode,
        )
    }
