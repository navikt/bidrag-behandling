package no.nav.bidrag.behandling.transformers.boforhold

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

fun Set<SivilstandGrunnlagDto>.tilSivilstandRequest(lagretSivilstand: Set<Sivilstand>? = emptySet()) =
    SivilstandRequest(
        this.toList(),
        lagretSivilstand?.toList()?.tilSivilstandBeregnV2Dto() ?: emptyList(),
        null,
    )

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

fun Set<Sivilstand>.tilSvilstandRequest() =
    SivilstandRequest(
        innhentedeOffentligeOpplysninger = emptyList(),
        behandledeSivilstandsopplysninger = toList().tilSivilstandBeregnV2Dto(),
        endreSivilstand = null,
    )

fun Sivilstandskode.tilSivilstandskodePDL() =
    when (this) {
        Sivilstandskode.BOR_ALENE_MED_BARN -> SivilstandskodePDL.SKILT
        Sivilstandskode.GIFT_SAMBOER -> SivilstandskodePDL.GIFT
        Sivilstandskode.SAMBOER -> SivilstandskodePDL.GIFT
        Sivilstandskode.ENSLIG -> SivilstandskodePDL.SKILT
        Sivilstandskode.UKJENT -> SivilstandskodePDL.UOPPGITT
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
            kilde = it.kilde,
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

fun Behandling.overskriveMedBearbeidaSivilstandshistorikk(nyHistorikk: Set<SivilstandBeregnV2Dto>) {
    this.sivilstand.clear()
    this.sivilstand.addAll(nyHistorikk.tilSivilstand(this))
}
