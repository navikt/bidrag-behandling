package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarn
import no.nav.bidrag.behandling.database.datamodell.BehandlingBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnDto
import no.nav.bidrag.behandling.dto.behandlingbarn.BehandlingBarnPeriodeDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetbarnetrygdDto
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto

fun Set<Sivilstand>.toSivilstandDto() = this.map {
    SivilstandDto(it.id, it.gyldigFraOgMed.toLocalDate(), it.datoTom?.toLocalDate(), it.sivilstandType)
}.toSet()

fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) = this.map {
    Sivilstand(behandling, it.gyldigFraOgMed.toDate(), it.datoTom?.toDate(), it.sivilstandType, it.id)
}.toMutableSet()

fun Set<Barnetillegg>.toBarnetilleggDto() = this.map {
    BarnetilleggDto(it.id, it.ident, it.barnetillegg, it.datoFom.toLocalDate(), it.datoTom.toLocalDate())
}.toSet()

fun Set<UtvidetbarnetrygdDto>.toUtvidetbarnetrygdDomain(behandling: Behandling) = this.map {
    Utvidetbarnetrygd(behandling, it.deltBoSted, it.belop, it.datoFom.toDate(), it.datoTom.toDate())
}.toMutableSet()

fun Set<Utvidetbarnetrygd>.toUtvidetbarnetrygdDto() = this.map {
    UtvidetbarnetrygdDto(it.id, it.deltBoSted, it.belop, it.datoFom.toLocalDate(), it.datoTom.toLocalDate())
}.toSet()

fun Set<BarnetilleggDto>.toBarnetilleggDomain(behandling: Behandling) = this.map {
    Barnetillegg(behandling, it.ident, it.barnetillegg, it.datoFom.toDate(), it.datoTom.toDate(), it.id)
}.toMutableSet()

fun Set<BehandlingBarnPeriode>.toBehandlingBarnPeriodeDto() = this.map {
    BehandlingBarnPeriodeDto(it.id, it.fraDato.toLocalDate(), it.tilDato.toLocalDate(), it.boStatus, it.kilde)
}.toSet()

fun Set<BehandlingBarnPeriodeDto>.toDomain(behandlingBarn: BehandlingBarn) = this.map {
    BehandlingBarnPeriode(behandlingBarn, it.fraDato.toDate(), it.tilDato.toDate(), it.boStatus, it.kilde)
}.toSet()

fun Set<BehandlingBarn>.toBehandlingBarnDto() = this.map {
    BehandlingBarnDto(it.id!!, it.medISaken, it.perioder.toBehandlingBarnPeriodeDto(), it.ident, it.navn, it.foedselsDato?.toLocalDate())
}.toSet()

fun Set<BehandlingBarnDto>.toDomain(behandling: Behandling) = this.map {
    val barn = BehandlingBarn(
        behandling,
        it.medISaken,
        it.id,
        it.ident,
        it.navn,
        it.foedselsDato?.toDate(),
    )
    barn.perioder = it.perioder.toDomain(barn).toMutableSet()
    barn
}.toMutableSet()

fun Set<InntektDto>.toInntektDomain(behandling: Behandling) = this.map {
    Inntekt(behandling, it.taMed, it.beskrivelse, it.belop, it.datoTom.toDate(), it.datoFom.toDate(), it.ident, it.fraGrunnlag,it.id)
}.toMutableSet()

fun Set<Inntekt>.toInntektDto() = this.map {
    InntektDto(it.id, it.taMed, it.beskrivelse, it.belop, it.datoTom.toLocalDate(), it.datoFom.toLocalDate(), it.ident, it.fraGrunnlag)
}.toSet()

fun Opplysninger.toDto(): OpplysningerDto {
    return OpplysningerDto(this.id!!, this.behandling.id!!, this.aktiv, this.opplysningerType, this.data, this.hentetDato.toLocalDate())
}
