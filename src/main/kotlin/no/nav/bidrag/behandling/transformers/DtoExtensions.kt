package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.UtvidetBarnetrygd
import no.nav.bidrag.behandling.dto.v1.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.v1.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.rolleManglerFødselsdato
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost

fun Set<Sivilstand>.toSivilstandDto() =
    this.map {
        no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.sivilstand,
            it.kilde,
        )
    }.sortedBy { it.datoFom }.toSet()

fun Set<no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto>.toSivilstandDomain(behandling: Behandling) =
    this.map {
        Sivilstand(behandling, it.datoFom, it.datoTom, it.sivilstand, it.kilde, it.id)
    }.toMutableSet()

fun Set<Barnetillegg>.toBarnetilleggDto() =
    this.map {
        BarnetilleggDto(
            it.id,
            it.ident,
            // TODO: Sett dette til gjelderBarn ident
            it.ident,
            it.barnetillegg,
            it.datoFom?.toLocalDate(),
            it.datoTom?.toLocalDate(),
        )
    }.toSet()

fun Set<UtvidetBarnetrygdDto>.toUtvidetBarnetrygdDomain(behandling: Behandling) =
    this.map {
        UtvidetBarnetrygd(
            behandling,
            it.deltBosted,
            it.beløp,
            it.datoFom,
            it.datoTom,
        )
    }.toMutableSet()

fun Set<UtvidetBarnetrygd>.toUtvidetBarnetrygdDto() =
    this.map {
        UtvidetBarnetrygdDto(
            it.id,
            it.deltBosted,
            it.belop,
            it.datoFom,
            it.datoTom,
        )
    }.toSet()

fun Set<BarnetilleggDto>.toBarnetilleggDomain(behandling: Behandling) =
    this.map {
        Barnetillegg(
            behandling,
            it.ident,
            it.barnetillegg,
            it.datoFom?.toDate(),
            it.datoTom?.toDate(),
            it.id,
        )
    }.toMutableSet()

fun Set<Husstandsbarnperiode>.toHusstandsBarnPeriodeDto() =
    this.map {
        no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto>.toDomain(husstandsBarn: Husstandsbarn) =
    this.map {
        Husstandsbarnperiode(
            husstandsBarn,
            it.datoFom,
            it.datoTom,
            it.bostatus!!,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.toHusstandsBarnDto(behandling: Behandling): Set<no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto> {
    val identerSøknadsbarn = behandling.getSøknadsbarn().map { sb -> sb.ident!! }.toSet()

    val søknadsbarn =
        this.filter { !it.ident.isNullOrBlank() && identerSøknadsbarn.contains(it.ident) }.map {
            it.toDto()
        }.sortedBy { it.fødselsdato }.toSet()

    val ikkeSøknadsbarnMenErMedISaken =
        this.filter { it.medISaken }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto() }
            .sortedBy { it.fødselsdato }.toSet()

    val andreHusstandsbarn =
        this.filter { !it.medISaken }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto() }
            .sortedBy { it.fødselsdato }.toSet()

    return søknadsbarn + ikkeSøknadsbarnMenErMedISaken + andreHusstandsbarn
}

fun Husstandsbarn.toDto(): no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto =
    no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto(
        this.id,
        this.medISaken,
        this.perioder.toHusstandsBarnPeriodeDto().sortedBy { periode -> periode.datoFom }.toSet(),
        this.ident,
        this.navn,
        this.foedselsdato,
    )

fun Set<no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto>.toDomain(behandling: Behandling) =
    this.map {
        val barn =
            Husstandsbarn(
                behandling,
                it.medISak,
                it.id,
                it.ident,
                it.navn,
                it.fødselsdato,
            )
        barn.perioder = it.perioder.toDomain(barn).toMutableSet()
        barn
    }.toMutableSet()

fun Set<InntektDto>.toInntektDomain(behandling: Behandling) =
    this.map {
        val inntekt =
            Inntekt(
                it.inntektstype,
                it.beløp,
                it.datoFom,
                it.datoTom,
                it.ident,
                it.fraGrunnlag,
                it.taMed,
                it.id,
                behandling,
            )
        inntekt.inntektsposter = it.inntektsposter.toInntektPostDomain(inntekt).toMutableSet()
        inntekt
    }.toMutableSet()

fun Set<InntektPost>.toInntektPostDomain(inntekt: Inntekt) =
    this.map {
        Inntektspost(it.beløp, it.kode, it.visningsnavn, inntekt = inntekt)
    }.toSet()

fun Set<Inntektspost>.toInntektPost() =
    this.map {
        InntektPost(it.kode, null, it.visningsnavn, it.beløp)
    }.toSet()

fun Set<Inntekt>.toInntektDto() =
    this.map {
        InntektDto(
            it.id,
            it.taMed,
            it.inntektstype,
            it.belop,
            it.datoFom,
            it.datoTom,
            it.ident,
            it.fraGrunnlag,
            it.inntektsposter.toInntektPost(),
        )
    }.toSet()

fun Grunnlag.toDto(): no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto {
    return no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto(
        this.id!!,
        this.behandling.id!!,
        this.type,
        this.data,
        this.innhentet,
    )
}

fun Behandling.tilForsendelseRolleDto() =
    roller.filter { r -> !(r.rolletype == Rolletype.BARN && r.ident == null) }.map {
        no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto(
            fødselsnummer = Personident(it.ident!!),
            type = it.rolletype,
        )
    }

fun no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto.toRolle(behandling: Behandling): Rolle =
    Rolle(
        behandling,
        rolletype = this.rolletype,
        this.ident?.verdi,
        this.fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
            ?: rolleManglerFødselsdato(rolletype),
        navn = this.navn,
    )
