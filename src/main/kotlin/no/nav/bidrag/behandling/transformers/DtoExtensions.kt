package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
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
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereManuellInntekt
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.rolleManglerFødselsdato
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import java.math.RoundingMode

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
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.toHusstandsBarnDto(behandling: Behandling): Set<HusstandsbarnDto> {
    val identerSøknadsbarn = behandling.søknadsbarn.map { sb -> sb.ident!! }.toSet()

    val søknadsbarn =
        this.filter { !it.ident.isNullOrBlank() && identerSøknadsbarn.contains(it.ident) }.map {
            it.toDto(behandling)
        }.sortedBy { it.fødselsdato }.toSet()

    val barnFraOffentligeKilderSomIkkeErDelAvBehandling =
        this.filter { Kilde.OFFENTLIG == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    val andreHusstandsbarn =
        this.filter { Kilde.MANUELL == it.kilde }.filter { !identerSøknadsbarn.contains(it.ident) }
            .map { it.toDto(behandling) }
            .sortedBy { it.fødselsdato }.toSet()

    return søknadsbarn + barnFraOffentligeKilderSomIkkeErDelAvBehandling + andreHusstandsbarn
}

fun Husstandsbarn.toDto(behandling: Behandling): HusstandsbarnDto =
    HusstandsbarnDto(
        this.id,
        this.kilde,
        !this.ident.isNullOrBlank() && behandling.søknadsbarn.map { it.ident }.contains(this.ident),
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
                it.kilde,
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
        ident = this.ident.verdi,
        gjelderBarn = this.gjelderBarn?.verdi,
        kilde = Kilde.MANUELL,
        taMed = this.taMed,
        id = this.id,
        behandling = behandling,
    )

fun Set<Inntektspost>.tilInntektspostDtoV2() =
    this.map {
        InntektspostDtoV2(
            kode = it.kode,
            visningsnavn = it.visningsnavn,
            inntektstype = it.inntektstype,
            beløp = it.beløp,
        )
    }

fun SummertMånedsinntekt.tilInntektDtoV2(gjelder: String) =
    InntektDtoV2(
        id = -1,
        taMed = false,
        rapporteringstype = Inntektsrapportering.AINNTEKT,
        beløp = sumInntekt.setScale(0, RoundingMode.HALF_UP),
        ident = Personident(gjelder),
        kilde = Kilde.OFFENTLIG,
        inntektsposter =
            inntektPostListe.map {
                InntektspostDtoV2(
                    kode = it.kode,
                    visningsnavn = it.visningsnavn,
                    inntektstype = it.inntekstype,
                    beløp = it.beløp.setScale(0, RoundingMode.HALF_UP),
                )
            }.toSet(),
        inntektstyper = emptySet(),
        datoFom = gjelderÅrMåned.atDay(1),
        datoTom = gjelderÅrMåned.atEndOfMonth(),
        opprinneligFom = gjelderÅrMåned.atDay(1),
        opprinneligTom = gjelderÅrMåned.atEndOfMonth(),
        gjelderBarn = null,
    )

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

fun Grunnlag.toDto(): GrunnlagsdataDto {
    return GrunnlagsdataDto(
        this.id!!,
        this.behandling.id!!,
        Personident(this.rolle.ident!!),
        Grunnlagstype(this.type, this.erBearbeidet),
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

fun OpprettRolleDto.toHusstandsbarn(behandling: Behandling): Husstandsbarn =
    Husstandsbarn(
        behandling,
        Kilde.OFFENTLIG,
        ident = this.ident?.verdi,
        foedselsdato =
            this.fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
                ?: rolleManglerFødselsdato(rolletype),
    )
