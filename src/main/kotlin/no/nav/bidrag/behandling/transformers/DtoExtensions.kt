package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnPeriodeDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetbarnetrygdDto
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.domain.ident.PersonIdent

fun Set<Sivilstand>.toSivilstandDto() = this.map {
    SivilstandDto(it.id, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.sivilstandType)
}.toSet()

fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) = this.map {
    Sivilstand(behandling, it.datoFom?.toDate(), it.datoTom?.toDate(), it.sivilstandType, it.id)
}.toMutableSet()

fun Set<Barnetillegg>.toBarnetilleggDto() = this.map {
    BarnetilleggDto(it.id, it.ident, it.barnetillegg, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate())
}.toSet()

fun Set<UtvidetbarnetrygdDto>.toUtvidetbarnetrygdDomain(behandling: Behandling) = this.map {
    Utvidetbarnetrygd(behandling, it.deltBoSted, it.belop, it.datoFom?.toDate(), it.datoTom?.toDate())
}.toMutableSet()

fun Set<Utvidetbarnetrygd>.toUtvidetbarnetrygdDto() = this.map {
    UtvidetbarnetrygdDto(it.id, it.deltBoSted, it.belop, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate())
}.toSet()

fun Set<BarnetilleggDto>.toBarnetilleggDomain(behandling: Behandling) = this.map {
    Barnetillegg(behandling, it.ident, it.barnetillegg, it.datoFom?.toDate(), it.datoTom?.toDate(), it.id)
}.toMutableSet()

fun Set<HusstandsBarnPeriode>.toHusstandsBarnPeriodeDto() = this.map {
    HusstandsBarnPeriodeDto(it.id, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.boStatus, it.kilde)
}.toSet()

fun Set<HusstandsBarnPeriodeDto>.toDomain(husstandsBarn: HusstandsBarn) = this.map {
    HusstandsBarnPeriode(husstandsBarn, it.datoFom?.toDate(), it.datoTom?.toDate(), it.boStatus, it.kilde)
}.toSet()

fun Set<HusstandsBarn>.toHusstandsBarnDto() = this.map {
    HusstandsBarnDto(it.id!!, it.medISaken, it.perioder.toHusstandsBarnPeriodeDto(), it.ident, it.navn, it.foedselsDato?.toLocalDate())
}.toSet()

fun Set<HusstandsBarnDto>.toDomain(behandling: Behandling) = this.map {
    val barn = HusstandsBarn(
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
    Inntekt(
        behandling, it.taMed, it.inntektType, it.belop,
        it.datoFom?.toDate(), it.datoTom?.toDate(), it.ident, it.fraGrunnlag, it.id,
    )
}.toMutableSet()

fun Set<Inntekt>.toInntektDto() = this.map {
    InntektDto(it.id, it.taMed, it.inntektType, it.belop, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.ident, it.fraGrunnlag)
}.toSet()

fun Opplysninger.toDto(): OpplysningerDto {
    return OpplysningerDto(this.id!!, this.behandling.id!!, this.aktiv, this.opplysningerType, this.data, this.hentetDato.toLocalDate())
}

fun Behandling.tilForsendelseRolleDto() = roller.map {
    ForsendelseRolleDto(
        fødselsnummer = PersonIdent(it.ident),
        type = when (it.rolleType) {
            RolleType.BIDRAGS_MOTTAKER -> Rolletype.BIDRAGSMOTTAKER
            RolleType.BIDRAGS_PLIKTIG -> Rolletype.BIDRAGSPLIKTIG
            RolleType.REELL_MOTTAKER -> Rolletype.REELMOTTAKER
            RolleType.BARN -> Rolletype.BARN
            RolleType.FEILREGISTRERT -> Rolletype.FEILREGISTRERT
        },
    )
}

fun SoknadType.tilVedtakType(): VedtakType = when (this) {
    SoknadType.FASTSETTELSE -> VedtakType.FASTSETTELSE
    SoknadType.REVURDERING -> VedtakType.REVURDERING
    SoknadType.ALDERSJUSTERING -> VedtakType.ALDERSJUSTERING
    SoknadType.ALDERSOPPHØR -> VedtakType.ALDERSOPPHØR
    SoknadType.ENDRING -> VedtakType.ENDRING
    SoknadType.ENDRING_MOTTAKER -> VedtakType.ENDRING_MOTTAKER
    SoknadType.KLAGE -> VedtakType.KLAGE
    SoknadType.OPPHØR -> VedtakType.OPPHØR
    SoknadType.INDEKSREGULERING -> VedtakType.INDEKSREGULERING
    SoknadType.INNKREVING -> VedtakType.INNKREVING
}
