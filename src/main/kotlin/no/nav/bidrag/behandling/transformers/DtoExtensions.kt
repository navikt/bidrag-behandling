package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Barnetillegg
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Opplysninger
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Soknadstype
import no.nav.bidrag.behandling.database.datamodell.UtvidetBarnetrygd
import no.nav.bidrag.behandling.dto.behandling.CreateRolleDto
import no.nav.bidrag.behandling.dto.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.forsendelse.ForsendelseRolleDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsBarnPeriodeDto
import no.nav.bidrag.behandling.dto.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.dto.opplysninger.OpplysningerDto
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost

fun Set<Sivilstand>.toSivilstandDto() =
    this.map {
        SivilstandDto(
            it.id,
<<<<<<< HEAD
            it.datoFom,
            it.datoTom,
            it.sivilstandstype.tilSivilstandskodeForBeregning(),
=======
            it.datoFom?.toLocalDate(),
            it.datoTom?.toLocalDate(),
            it.sivilstand,
>>>>>>> main
            it.kilde,
        )
    }.toSet()

fun Set<SivilstandDto>.toSivilstandDomain(behandling: Behandling) =
    this.map {
<<<<<<< HEAD
        Sivilstand(behandling, it.datoFom, it.datoTom, it.sivilstand.name, it.kilde, it.id)
=======
        Sivilstand(
            behandling,
            it.datoFom?.toDate(),
            it.datoTom?.toDate(),
            it.sivilstand,
            it.kilde,
            it.id,
        )
>>>>>>> main
    }.toMutableSet()

fun Set<Barnetillegg>.toBarnetilleggDto() =
    this.map {
        BarnetilleggDto(
            it.id,
            it.ident,
            it.barnetillegg,
            it.datoFom?.toLocalDate(),
            it.datoTom?.toLocalDate(),
        )
    }.toSet()

fun Set<UtvidetBarnetrygdDto>.toUtvidetbarnetrygdDomain(behandling: Behandling) =
    this.map {
        UtvidetBarnetrygd(
            behandling,
            it.deltBoSted,
            it.belop,
            it.datoFom,
            it.datoTom,
        )
    }.toMutableSet()

fun Set<UtvidetBarnetrygd>.toUtvidetbarnetrygdDto() =
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
        HusstandsBarnPeriodeDto(
            it.id,
<<<<<<< HEAD
            it.datoFom,
            it.datoTom,
=======
            it.datoFom?.toLocalDate(),
            it.datoTom?.toLocalDate(),
>>>>>>> main
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<HusstandsBarnPeriodeDto>.toDomain(husstandsBarn: Husstandsbarn) =
    this.map {
        Husstandsbarnperiode(
            husstandsBarn,
<<<<<<< HEAD
            it.datoFom,
            it.datoTom,
=======
            it.datoFom?.toDate(),
            it.datoTom?.toDate(),
>>>>>>> main
            it.bostatus,
            it.kilde,
        )
    }.toSet()

fun Set<Husstandsbarn>.toHusstandsBarnDto() =
    this.map {
        HusstandsbarnDto(
            it.id!!,
            it.medISaken,
            it.perioder.toHusstandsBarnPeriodeDto(),
            it.ident,
            it.navn,
            it.foedselsdato,
        )
    }.toSet()

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
        InntektPost(it.kode, it.visningsnavn, it.beløp)
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

fun Opplysninger.toDto(): OpplysningerDto {
    return OpplysningerDto(
        this.id!!,
        this.behandling.id!!,
        this.opplysningerType,
        this.data,
        this.hentetDato.toLocalDate(),
    )
}

fun Behandling.tilForsendelseRolleDto() =
    roller.filter { r -> !(r.rolletype == Rolletype.BARN && r.ident == null) }.map {
        ForsendelseRolleDto(
            fødselsnummer = Personident(it.ident!!),
            type = it.rolletype,
        )
    }

fun CreateRolleDto.toRolle(behandling: Behandling): Rolle =
    Rolle(
        behandling,
<<<<<<< HEAD
        rolletype = this.rolletype,
        this.ident,
        this.fødselsdato,
        this.opprettetdato,
=======
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
>>>>>>> main
        navn = this.navn,
    )

fun Soknadstype.tilVedtakType(): Vedtakstype =
    when (this) {
        Soknadstype.FASTSETTELSE -> Vedtakstype.FASTSETTELSE
        Soknadstype.REVURDERING -> Vedtakstype.REVURDERING
        Soknadstype.ALDERSJUSTERING -> Vedtakstype.ALDERSJUSTERING
        Soknadstype.ALDERSOPPHØR -> Vedtakstype.ALDERSOPPHØR
        Soknadstype.ENDRING -> Vedtakstype.ENDRING
        Soknadstype.ENDRING_MOTTAKER -> Vedtakstype.ENDRING_MOTTAKER
        Soknadstype.KLAGE -> Vedtakstype.KLAGE
        Soknadstype.OPPHØR -> Vedtakstype.OPPHØR
        Soknadstype.INDEKSREGULERING -> Vedtakstype.INDEKSREGULERING
        Soknadstype.INNKREVING -> Vedtakstype.INNKREVING
    }
