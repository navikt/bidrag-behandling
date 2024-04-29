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

fun BoforholdResponse.tilBostatus() =
    Bostatus(
        bostatus = this.bostatus,
        kilde = this.kilde,
        periodeFom = this.periodeFom,
        periodeTom = this.periodeTom,
    )

fun List<BoforholdResponse>.konvertereTilBoforholdRequest() =
    this.groupBy { it.relatertPersonPersonId }.map {
        BoforholdRequest(
            bostatusListe = it.value.map { it.tilBostatus() },
            // TODO: Alltid sant for forskudd, men oppdatere med nytt felt fra BoforholdResponse når dette
            //  gjøres tilgjengelig
            erBarnAvBmBp = true,
            fødselsdato = it.value.first().fødselsdato,
            relatertPersonPersonId = it.key,
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

fun List<BoforholdResponse>.tilHusstandsbarn(
    behandling: Behandling,
    originalHusstandsbarn: Husstandsbarn? = null,
): Set<Husstandsbarn> {
    return this.groupBy { it.relatertPersonPersonId }.map {
        if (originalHusstandsbarn != null && it.key != originalHusstandsbarn.ident) {
            return@map originalHusstandsbarn
        }
        val fødselsdatoFraRespons = it.value.first().fødselsdato
        val husstandsbarn =
            originalHusstandsbarn ?: Husstandsbarn(
                behandling = behandling,
                kilde = Kilde.OFFENTLIG,
                ident = it.key,
                fødselsdato = finnFødselsdato(it.key, fødselsdatoFraRespons) ?: fødselsdatoFraRespons,
            )
        husstandsbarn.perioder.clear()
        husstandsbarn.perioder.addAll(
            it.value.map { boforhold ->
                Husstandsbarnperiode(
                    bostatus = boforhold.bostatus,
                    datoFom = boforhold.periodeFom,
                    datoTom = boforhold.periodeTom,
                    kilde = boforhold.kilde,
                    husstandsbarn = husstandsbarn,
                )
            }.toMutableSet(),
        )
        husstandsbarn
    }.toSet()
}

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
