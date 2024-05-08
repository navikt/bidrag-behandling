package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

fun Set<SivilstandGrunnlagDto>.tilSivilstandRequest(manuellePerioder: List<SivilstandBeregnV2Dto>? = emptyList()) =
    SivilstandRequest(
        this.toList(),
        manuellePerioder ?: emptyList(),
    )

/*

fun Set<Sivilstand>.tilSivilstandRequest(offentligePerioder: List<SivilstandGrunnlagDto> = emptyList()) =
    SivilstandRequest(offentligePerioder, this.filter { Kilde.MANUELL == it.kilde }.tilSivilstandBeregnV2Dto())
*/

fun Set<SivilstandBeregnV2Dto>.tilSivilstandRequest(sivilstand: Set<Sivilstand>) : SivilstandRequest {
    return SivilstandRequest(
        offentligePerioder = this.tilSvilstandGrunnlagDto(sivilstand.first().behandling.bidragsmottaker!!.ident!!),
        manuellePerioder = sivilstand.filter { Kilde.MANUELL == it.kilde }.tilSivilstandBeregnV2Dto(),
    )
}

fun Set<SivilstandBeregnV2Dto>.tilSvilstandGrunnlagDto(personident: String) =
    this.map {
        SivilstandGrunnlagDto(
            gyldigFom = it.periodeFom,
            personId = personident,
            type = it.sivilstandskode.tilSivilstandskodePDL(),
            bekreftelsesdato = null,
            historisk = null,
            master = null,
            registrert = null,
        )
    }

fun List<Sivilstand>.tilSivilstandBeregnV2Dto() =
    this.map {
        SivilstandBeregnV2Dto(
            periodeFom = it.datoFom!!,
            periodeTom = it.datoTom,
            kilde = it.kilde,
            sivilstandskode = it.sivilstand,
        )
    }

fun Set<SivilstandBeregnV2Dto>.tilSivilstand(behandling: Behandling): List<Sivilstand> =
    this.map {
        Sivilstand(
            behandling = behandling,
            kilde = Kilde.OFFENTLIG,
            datoFom = it.periodeFom,
            datoTom = it.periodeTom,
            sivilstand = it.sivilstandskode,
        )
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
