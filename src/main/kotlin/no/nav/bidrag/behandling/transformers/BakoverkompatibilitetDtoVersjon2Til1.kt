package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.InntekterDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereInntekterRequest
import no.nav.bidrag.behandling.dto.v1.husstandsbarn.HusstandsbarnDto
import no.nav.bidrag.behandling.dto.v1.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.v1.inntekt.KontantstøtteDto
import no.nav.bidrag.behandling.dto.v1.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.boforhold.BoforholdDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.HusstandsbarnDtoV2
import no.nav.bidrag.behandling.dto.v2.boforhold.OppdatereBoforholdRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import java.math.BigDecimal

/**
 * Inneholder omgjøringer som kreves for å støtte bakoverkompatibilitet. Fila skal kunne slettes når migrering til API V2 er fullført.
 */

fun BehandlingDtoV2.tilBehandlingDto() =

    BehandlingDto(
        id = id,
        vedtakstype = vedtakstype,
        stønadstype = stønadstype,
        engangsbeløptype = engangsbeløptype,
        erVedtakFattet = erVedtakFattet,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        søktAv = søktAv,
        saksnummer = saksnummer,
        søknadsid = søknadsid,
        behandlerenhet = behandlerenhet,
        roller = roller,
        søknadRefId = søknadRefId,
        grunnlagspakkeid = grunnlagspakkeid,
        virkningstidspunkt = virkningstidspunkt,
        boforhold = boforhold.tilBoforholdDto(),
        inntekter = inntekter.tilInntekterDto(),
        opplysninger = aktiveGrunnlagsdata.toList(),
    )

fun InntekterDtoV2.tilInntekterDto(): InntekterDto {
    return InntekterDto(
        inntekter = årsinntekter.map { it.tilInntektDto() }.toSet(),
        barnetillegg = barnetillegg.map { it.tilBarnetilleggDto() }.toSet(),
        utvidetbarnetrygd = utvidetBarnetrygd.map { it.tilUtvidetBarnetrygdDto() }.toSet(),
        kontantstøtte = kontantstøtte.map { it.tilKontantstøtteDto() }.toSet(),
        småbarnstillegg = småbarnstillegg.map { it.tilInntektDto() }.toSet(),
        notat = notat,
    )
}

fun InntektDtoV2.tilOppdatereManuellInntekt() =
    OppdatereManuellInntekt(
        id = id,
        taMed = taMed,
        type = rapporteringstype,
        beløp = beløp,
        datoFom = datoFom,
        datoTom = datoTom,
        ident = ident,
        gjelderBarn = gjelderBarn,
    )

fun InntektDtoV2.tilInntektDto() =
    InntektDto(
        id = id,
        taMed = taMed,
        inntektstype = rapporteringstype,
        beløp = beløp,
        datoFom = datoFom,
        datoTom = datoTom,
        ident = ident.verdi,
        fraGrunnlag = kilde == Kilde.OFFENTLIG,
        inntektsposter = inntektsposter.tilInntektspostDto(),
    )

fun InntektDtoV2.tilBarnetilleggDto() =
    BarnetilleggDto(
        id = id,
        ident = ident.verdi,
        gjelderBarn = gjelderBarn!!.verdi,
        barnetillegg = beløp,
        datoFom = datoFom,
        datoTom = datoTom,
    )

fun InntektDtoV2.tilKontantstøtteDto() =
    KontantstøtteDto(
        ident = ident.verdi,
        gjelderBarn = gjelderBarn?.verdi ?: "",
        kontantstøtte = beløp,
        datoFom = this.datoFom,
        datoTom = datoTom,
    )

fun InntektDtoV2.tilUtvidetBarnetrygdDto() =
    UtvidetBarnetrygdDto(
        id = id,
        deltBosted = false,
        beløp = beløp,
        datoFom = datoFom,
        datoTom = datoTom,
    )

fun Set<InntektspostDtoV2>.tilInntektspostDto() =
    this.map {
        InntektPost(
            kode = it.kode,
            visningsnavn = it.visningsnavn,
            beløp = it.beløp ?: BigDecimal.ZERO,
        )
    }.toSet()

fun BoforholdDtoV2.tilBoforholdDto() =
    BoforholdDto(
        husstandsbarn = this.husstandsbarn.tilHusstandsbarnDto(),
        sivilstand = this.sivilstand,
        notat = this.notat,
        valideringsfeil = this.valideringsfeil,
    )

fun Set<HusstandsbarnDtoV2>.tilHusstandsbarnDto() =
    this.map {
        HusstandsbarnDto(
            id = it.id,
            kilde = it.kilde,
            fødselsdato = it.fødselsdato,
            medISak = it.medIBehandling,
            perioder = it.perioder,
        )
    }.toSet()

fun OppdaterBehandlingRequest.tilOppdaterBehandlingRequestV2(personidentBm: Personident): OppdaterBehandlingRequestV2 {
    return OppdaterBehandlingRequestV2(
        virkningstidspunkt = this.virkningstidspunkt,
        boforhold = this.boforhold?.tilOppdatereBoforholdRequestV2(),
        inntekter = this.inntekter?.tilOppdatereInntekterRequestV2(personidentBm),
    )
}

fun OppdatereInntekterRequest.tilOppdatereInntekterRequestV2(personidentBm: Personident): OppdatereInntekterRequestV2 {
    val inntekt =
        this.inntekter.orEmpty().map { i -> i.tilInntektDtoV2() }
            .map { it.tilOppdatereManuellInntekt() }.toMutableSet()
    val barnetillegg =
        this.barnetillegg.orEmpty().map { bt -> bt.tilInntektDtoV2() }
            .map { it.tilOppdatereManuellInntekt() }.toSet()
    val kontantstøtte =
        this.kontantstøtte.orEmpty().map { bt -> bt.tilInntektDtoV2() }
            .map { it.tilOppdatereManuellInntekt() }.toSet()
    val utvidetBarnetrygd =
        this.utvidetbarnetrygd.orEmpty().map { ubt -> ubt.tilInntektDtoV2(personidentBm) }
            .map { it.tilOppdatereManuellInntekt() }.toSet()

    return OppdatereInntekterRequestV2(
        oppdatereManuelleInntekter =
            inntekt.filter { inntektDto ->
                Inntektsrapportering.entries.filter { i -> i.kanLeggesInnManuelt }
                    .contains(inntektDto.type)
            }.toSet() + barnetillegg + kontantstøtte + utvidetBarnetrygd,
        notat = this.notat,
    )
}

fun OppdaterBoforholdRequest.tilOppdatereBoforholdRequestV2() =
    OppdatereBoforholdRequestV2(
        oppdatereNotat = this.notat,
    )

fun BarnetilleggDto.tilInntektDtoV2() =
    InntektDtoV2(
        taMed = true,
        rapporteringstype = Inntektsrapportering.BARNETILLEGG,
        beløp = this.barnetillegg,
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        opprinneligFom = this.datoFom,
        opprinneligTom = this.datoTom,
        ident = Personident(this.ident),
        gjelderBarn = Personident(this.gjelderBarn),
        kilde = Kilde.MANUELL,
        inntektsposter = emptySet(),
        inntektstyper = Inntektsrapportering.BARNETILLEGG.inneholderInntektstypeListe.toSet(),
    )

fun InntektDto.tilInntektDtoV2() =
    InntektDtoV2(
        taMed = this.taMed,
        rapporteringstype = this.inntektstype,
        beløp = this.beløp,
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        opprinneligFom = this.opprinneligFom,
        opprinneligTom = this.opprinneligTom,
        ident = Personident(this.ident),
        gjelderBarn = null,
        kilde = if (this.fraGrunnlag) Kilde.OFFENTLIG else Kilde.MANUELL,
        inntektsposter =
            this.inntektsposter.tilInntektspostDtoV2(
                this.inntektstype.inneholderInntektstypeListe.getOrElse(0) {
                    Inntektstype.LØNNSINNTEKT
                },
            ).toSet(),
        inntektstyper = this.inntektstype.inneholderInntektstypeListe.toSet(),
    )

fun Set<InntektPost>.tilInntektspostDtoV2(inntektstype: Inntektstype) =
    this.map {
        InntektspostDtoV2(
            kode = it.kode,
            beløp = it.beløp,
            visningsnavn = it.visningsnavn,
            inntektstype = inntektstype,
        )
    }

fun KontantstøtteDto.tilInntektDtoV2() =
    InntektDtoV2(
        taMed = true,
        rapporteringstype = Inntektsrapportering.KONTANTSTØTTE,
        beløp = this.kontantstøtte,
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        opprinneligFom = this.datoFom,
        opprinneligTom = this.datoTom,
        ident = Personident(this.ident),
        gjelderBarn = Personident(this.gjelderBarn),
        kilde = Kilde.MANUELL,
        inntektsposter = emptySet(),
        inntektstyper = Inntektsrapportering.KONTANTSTØTTE.inneholderInntektstypeListe.toSet(),
    )

fun UtvidetBarnetrygdDto.tilInntektDtoV2(personidentBm: Personident) =
    InntektDtoV2(
        taMed = true,
        rapporteringstype = Inntektsrapportering.UTVIDET_BARNETRYGD,
        beløp = this.beløp,
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        opprinneligFom = this.datoFom,
        opprinneligTom = this.datoTom,
        ident = personidentBm,
        gjelderBarn = null,
        kilde = Kilde.MANUELL,
        inntektsposter = emptySet(),
        inntektstyper = Inntektsrapportering.UTVIDET_BARNETRYGD.inneholderInntektstypeListe.toSet(),
    )
