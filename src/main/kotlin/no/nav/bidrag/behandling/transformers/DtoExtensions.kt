package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v1.grunnlag.GrunnlagsdataDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnperiodeDto
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.rolleManglerFødselsdato
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import java.math.BigDecimal

fun Set<Sivilstand>.toSivilstandDto() =
    this.map {
        SivilstandDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.sivilstand,
            it.kilde,
        )
    }.sortedBy { it.datoFom }.toSet()

fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) =
    this.map {
        Sivilstand(behandling, it.datoFom, it.datoTom, it.sivilstand, it.kilde, it.id)
    }.toMutableSet()

fun Set<Husstandsbarnperiode>.toHusstandsBarnPeriodeDto() =
    this.map {
        HusstandsbarnperiodeDto(
            it.id,
            it.datoFom,
            it.datoTom,
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<HusstandsbarnperiodeDto>.toDomain(husstandsBarn: Husstandsbarn) =
    this.map {
        Husstandsbarnperiode(
            husstandsBarn,
            it.datoFom,
            it.datoTom,
            it.bostatus!!,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.toHusstandsBarnDto(behandling: Behandling): Set<HusstandsbarnDto> {
    val identerSøknadsbarn = behandling.søknadsbarn.map { sb -> sb.ident!! }.toSet()

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

fun Husstandsbarn.toDto(): HusstandsbarnDto =
    HusstandsbarnDto(
        this.id,
        this.medISaken,
        this.perioder.toHusstandsBarnPeriodeDto().sortedBy { periode -> periode.datoFom }.toSet(),
        this.ident,
        this.navn,
        this.foedselsdato,
    )

fun Set<HusstandsbarnDto>.toDomain(behandling: Behandling) =
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

fun OppdatereManuellInntekt.tilInntekt(inntekt: Inntekt): Inntekt {
    inntekt.type = this.type
    inntekt.belop = this.beløp
    inntekt.datoFom = this.datoFom
    inntekt.datoTom = this.datoTom
    inntekt.gjelderBarn = this.gjelderBarn?.verdi
    inntekt.kilde = Kilde.MANUELL
    inntekt.taMed = this.taMed
    return inntekt
}

fun OppdatereManuellInntekt.tilInntekt(behandling: Behandling) =
    Inntekt(
        type = this.type,
        belop = this.beløp,
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        opprinneligTom = it.opprinneligTom,
                opprinneligFom = it.opprinneligFom,
                ident = this.ident.verdi,
        gjelderBarn = this.gjelderBarn?.verdi,
        kilde = Kilde.MANUELL,
        taMed = this.taMed,
        id = this.id,
        behandling = behandling,
    )

fun Set<InntektspostDtoV2>.tilInntektspost(inntekt: Inntekt) =
    this.map {
        Inntektspost(
            it.beløp ?: BigDecimal.ZERO,
            it.kode,
            it.visningsnavn,
            inntekt = inntekt,
            inntektstype = it.inntektstype,
        )
    }

fun Set<Inntektspost>.tilInntektspostDtoV2() =
    this.map {
        InntektspostDtoV2(
            kode = it.kode,
            visningsnavn = it.visningsnavn,
            inntektstype = it.inntektstype,
            beløp = it.beløp,
        )
    }

fun List<Inntekt>.tilInntektDtoV2() =
    this.map {
        InntektDtoV2(
            id = it.id,
            taMed = it.taMed,
            rapporteringstype = it.type,
            beløp = it.belop,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            ident = Personident(it.ident),
            gjelderBarn = it.gjelderBarn?.let { it1 -> Personident(it1) },
            kilde = it.kilde,
            inntektsposter = it.inntektsposter.tilInntektspostDtoV2().toSet(),
            inntektstyper = it.type.inneholderInntektstypeListe.toSet(),
            opprinneligFom = it.opprinneligFom,
            opprinneligTom = it.opprinneligTom,
        )
    }

fun BehandlingGrunnlag.toDto(): GrunnlagsdataDto {
    return GrunnlagsdataDto(
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

fun OpprettRolleDto.toRolle(behandling: Behandling): Rolle =
    Rolle(
        behandling,
        rolletype = this.rolletype,
        this.ident?.verdi,
        this.fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
            ?: rolleManglerFødselsdato(rolletype),
        navn = this.navn,
    )
