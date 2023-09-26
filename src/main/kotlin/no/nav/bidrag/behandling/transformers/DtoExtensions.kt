package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarnPeriode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.InntektPostDomain
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.SoknadType
import no.nav.bidrag.behandling.database.datamodell.Utvidetbarnetrygd
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.CreateRolleRolleType
import no.nav.bidrag.behandling.dto.behandling.RolleTypeDto
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
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost

fun Set<Sivilstand>.toSivilstandDto() =
    this.map {
        SivilstandDto(it.id, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.sivilstandType)
    }.toSet()

fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) =
    this.map {
        Sivilstand(behandling, it.datoFom?.toDate(), it.datoTom?.toDate(), it.sivilstandType, it.id)
    }.toMutableSet()

fun Set<Barnetillegg>.toBarnetilleggDto() =
    this.map {
        BarnetilleggDto(it.id, it.ident, it.barnetillegg, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate())
    }.toSet()

fun Set<UtvidetbarnetrygdDto>.toUtvidetbarnetrygdDomain(behandling: Behandling) =
    this.map {
        Utvidetbarnetrygd(behandling, it.deltBoSted, it.belop, it.datoFom?.toDate(), it.datoTom?.toDate())
    }.toMutableSet()

fun Set<Utvidetbarnetrygd>.toUtvidetbarnetrygdDto() =
    this.map {
        UtvidetbarnetrygdDto(it.id, it.deltBoSted, it.belop, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate())
    }.toSet()

fun Set<BarnetilleggDto>.toBarnetilleggDomain(behandling: Behandling) =
    this.map {
        Barnetillegg(behandling, it.ident, it.barnetillegg, it.datoFom?.toDate(), it.datoTom?.toDate(), it.id)
    }.toMutableSet()

fun Set<HusstandsBarnPeriode>.toHusstandsBarnPeriodeDto() =
    this.map {
        HusstandsBarnPeriodeDto(it.id, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.boStatus, it.kilde)
    }.toSet()

fun Set<HusstandsBarnPeriodeDto>.toDomain(husstandsBarn: HusstandsBarn) =
    this.map {
        HusstandsBarnPeriode(husstandsBarn, it.datoFom?.toDate(), it.datoTom?.toDate(), it.boStatus, it.kilde)
    }.toSet()

fun Set<HusstandsBarn>.toHusstandsBarnDto() =
    this.map {
        HusstandsBarnDto(it.id!!, it.medISaken, it.perioder.toHusstandsBarnPeriodeDto(), it.ident, it.navn, it.foedselsDato?.toLocalDate())
    }.toSet()

fun Set<HusstandsBarnDto>.toDomain(behandling: Behandling) =
    this.map {
        val barn =
            HusstandsBarn(
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

fun Set<InntektDto>.toInntektDomain(behandling: Behandling) =
    this.map {
        val inntekt =
            Inntekt(
                behandling, it.taMed, it.inntektType, it.belop,
                it.datoFom?.toDate(), it.datoTom?.toDate(), it.ident, it.fraGrunnlag, it.id,
            )
        inntekt.inntektPostListe = it.inntektPostListe.toInntektPostDomain(inntekt).toMutableSet()
        inntekt
    }.toMutableSet()

fun Set<InntektPost>.toInntektPostDomain(inntekt: Inntekt) =
    this.map {
        InntektPostDomain(inntekt, it.beløp, it.kode, it.visningsnavn)
    }.toSet()

fun Set<InntektPostDomain>.toInntektPost() =
    this.map {
        InntektPost(it.kode, it.visningsnavn, it.beløp)
    }.toSet()

fun Set<Inntekt>.toInntektDto() =
    this.map {
        InntektDto(it.id, it.taMed, it.inntektType, it.belop, it.datoFom?.toLocalDate(), it.datoTom?.toLocalDate(), it.ident, it.fraGrunnlag, it.inntektPostListe.toInntektPost())
    }.toSet()

fun Opplysninger.toDto(): OpplysningerDto {
    return OpplysningerDto(this.id!!, this.behandling.id!!, this.opplysningerType, this.data, this.hentetDato.toLocalDate())
}

fun Behandling.tilForsendelseRolleDto() =
    roller.map {
        ForsendelseRolleDto(
            fødselsnummer = PersonIdent(it.ident),
            type = it.rolleType,
        )
    }

fun CreateRolleDto.toRolle(behandling: Behandling): Rolle =
    Rolle(
        behandling,
        rolleType =
        when (this.rolleType) {
            CreateRolleRolleType.BIDRAGS_MOTTAKER -> Rolletype.BIDRAGSMOTTAKER
            CreateRolleRolleType.BIDRAGS_PLIKTIG -> Rolletype.BIDRAGSPLIKTIG
            CreateRolleRolleType.REELL_MOTTAKER -> Rolletype.REELMOTTAKER
            CreateRolleRolleType.BARN -> Rolletype.BARN
            CreateRolleRolleType.FEILREGISTRERT -> Rolletype.FEILREGISTRERT
        },
        this.ident,
        this.fodtDato,
        this.opprettetDato,
    )

fun SoknadType.tilVedtakType(): VedtakType =
    when (this) {
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

fun Rolletype.toRolleTypeDto(): RolleTypeDto =
    when (this) {
        Rolletype.BARN -> RolleTypeDto.BARN
        Rolletype.BIDRAGSMOTTAKER -> RolleTypeDto.BIDRAGSMOTTAKER
        Rolletype.BIDRAGSPLIKTIG -> RolleTypeDto.BIDRAGSPLIKTIG
        Rolletype.FEILREGISTRERT -> RolleTypeDto.FEILREGISTRERT
        Rolletype.REELMOTTAKER -> RolleTypeDto.REELMOTTAKER
    }
