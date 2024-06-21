package no.nav.bidrag.behandling.transformers.boforhold

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.boforhold.Sivilstandsperiode
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.diverse.TypeEndring
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.sivilstand.dto.EndreSivilstand
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.time.LocalDate
import no.nav.bidrag.sivilstand.dto.Sivilstand as SivilstandBeregnV2Dto

private val log = KotlinLogging.logger {}

fun Set<SivilstandGrunnlagDto>.tilSivilstandRequest(
    lagretSivilstand: Set<Sivilstand>? = emptySet(),
    fødselsdatoBm: LocalDate,
) = SivilstandRequest(
    innhentedeOffentligeOpplysninger = this.toList(),
    behandledeSivilstandsopplysninger = lagretSivilstand?.toList()?.tilSivilstandBeregnV2Dto() ?: emptyList(),
    fødselsdatoBM = fødselsdatoBm,
    endreSivilstand = null,
)

fun Behandling.henteNyesteSivilstandGrunnlagsdata(): List<SivilstandGrunnlagDto> {
    return grunnlag.hentSisteAktiv()
        .find { !it.erBearbeidet && Grunnlagsdatatype.SIVILSTAND == it.type }
        .konvertereData<List<SivilstandGrunnlagDto>>() ?: emptyList()
}

fun Set<Sivilstand>.tilSvilstandRequest(
    nyttEllerEndretInnslag: Sivilstandsperiode? = null,
    sletteInnslag: Long? = null,
    fødselsdatoBm: LocalDate,
    behandling: Behandling,
) = SivilstandRequest(
    innhentedeOffentligeOpplysninger = behandling.henteNyesteSivilstandGrunnlagsdata(),
    behandledeSivilstandsopplysninger = toList().tilSivilstandBeregnV2Dto(),
    endreSivilstand = tilEndreSivilstand(nyttEllerEndretInnslag, sletteInnslag),
    fødselsdatoBM = fødselsdatoBm,
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
            periodeFom = it.datoFom,
            periodeTom = it.datoTom,
            kilde = it.kilde,
            sivilstandskode = it.sivilstand,
        )
    }

fun Sivilstand.tilSivilstandBeregnV2Dto() =
    SivilstandBeregnV2Dto(
        periodeFom = this.datoFom,
        periodeTom = this.datoTom,
        kilde = this.kilde,
        sivilstandskode = this.sivilstand,
    )

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

private fun Set<Sivilstand>.tilEndreSivilstand(
    nyttEllerEndretInnslag: Sivilstandsperiode? = null,
    sletteInnslag: Long? = null,
): EndreSivilstand? {
    try {
        if (nyttEllerEndretInnslag == null && sletteInnslag == null) {
            return null
        }
        return EndreSivilstand(
            typeEndring = bestemmeEndringstype(nyttEllerEndretInnslag, sletteInnslag),
            nySivilstand = bestemmeNySivilstand(nyttEllerEndretInnslag),
            originalSivilstand = bestemmmeOriginalSivilstand(nyttEllerEndretInnslag, sletteInnslag),
        )
    } catch (illegalArgumentException: IllegalArgumentException) {
        val behandlingsid =
            if (this.isNotEmpty()) {
                this.first().behandling.id
            } else {
                "ukjent"
            }
        log.warn {
            "Mottok mangelfulle opplysninger ved oppdatering av sivilstand i behandling $behandlingsid. " +
                "Mottatt input: nyttEllerEndretInnslag=$nyttEllerEndretInnslag, " +
                "sletteInnslag=$sletteInnslag"
        }
        oppdateringAvBoforholdFeilet(
            "Oppdatering av sivilstand i behandling $behandlingsid feilet pga mangelfulle inputdata",
        )
    }
}

fun Set<Sivilstand>.bestemmmeOriginalSivilstand(
    nyttEllerEndretInnslag: Sivilstandsperiode?,
    sletteInnslag: Long?,
): SivilstandBeregnV2Dto? {
    nyttEllerEndretInnslag?.id?.let { id -> return this.find { it.id == id }?.tilSivilstandBeregnV2Dto() }
    sletteInnslag?.let { id -> return this.find { it.id == id }?.tilSivilstandBeregnV2Dto() } ?: return null
}

fun bestemmeNySivilstand(nyttEllerEndretInnslag: Sivilstandsperiode?): SivilstandBeregnV2Dto? {
    return nyttEllerEndretInnslag?.let {
        SivilstandBeregnV2Dto(
            periodeFom = it.fraOgMed,
            periodeTom = it.tilOgMed,
            kilde = Kilde.MANUELL,
            sivilstandskode = it.sivilstand,
        )
    }
}

private fun bestemmeEndringstype(
    nyttEllerEndretInnslag: Sivilstandsperiode? = null,
    sletteInnslag: Long? = null,
): TypeEndring {
    nyttEllerEndretInnslag?.let {
        if (it.id != null) {
            return TypeEndring.ENDRET
        }
        return TypeEndring.NY
    }

    if (sletteInnslag != null) {
        return TypeEndring.SLETTET
    }

    throw IllegalArgumentException(
        "Mangler data til å avgjøre endringstype. Motttok input: nyttEllerEndretInnslag: $nyttEllerEndretInnslag, " +
            " sletteInnslag: $sletteInnslag",
    )
}
