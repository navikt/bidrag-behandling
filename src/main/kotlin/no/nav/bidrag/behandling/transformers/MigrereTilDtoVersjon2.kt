package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingDto
import no.nav.bidrag.behandling.dto.v1.behandling.InntekterDto
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBehandlingRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereInntekterRequest
import no.nav.bidrag.behandling.dto.v1.inntekt.BarnetilleggDto
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
import no.nav.bidrag.behandling.dto.v1.inntekt.KontantstøtteDto
import no.nav.bidrag.behandling.dto.v1.inntekt.UtvidetBarnetrygdDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdatereInntekterRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import java.math.BigDecimal

val rapporteringstyperSomIkkeSkalInkluderesIInntekt =
    setOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.SMÅBARNSTILLEGG,
        Inntektsrapportering.UTVIDET_BARNETRYGD,
    )

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
        boforhold = boforhold,
        inntekter = inntekter.tilInntekterDto(),
        opplysninger = opplysninger,
    )

fun InntekterDtoV2.tilInntekterDto(): InntekterDto {
    return InntekterDto(
        inntekter =
            inntekter.filter { !rapporteringstyperSomIkkeSkalInkluderesIInntekt.contains(it.rapporteringstype) }
                .map { it.tilInntektDto() }.toSet(),
        barnetillegg =
            inntekter.filter { it.rapporteringstype == Inntektsrapportering.BARNETILLEGG }
                .map { it.tilBarnetilleggDto() }.toSet(),
        utvidetbarnetrygd =
            inntekter.filter { it.rapporteringstype == Inntektsrapportering.UTVIDET_BARNETRYGD }
                .map { it.tilUtvidetBarnetrygdDto() }.toSet(),
        kontantstøtte =
            inntekter.filter { it.rapporteringstype == Inntektsrapportering.KONTANTSTØTTE }
                .map { it.tilKontantstøtteDto() }.toSet(),
        småbarnstillegg =
            inntekter.filter { it.rapporteringstype == Inntektsrapportering.SMÅBARNSTILLEGG }
                .map { it.tilInntektDto() }.toSet(),
        notat = notat,
    )
}

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
        gjelderBarn = gjelderBarn!!.verdi,
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

fun OppdaterBehandlingRequest.tilOppdaterBehandlingRequestV2(personidentBm: Personident): OppdaterBehandlingRequestV2 {
    return OppdaterBehandlingRequestV2(
        grunnlagspakkeId = this.grunnlagspakkeId,
        vedtaksid = this.vedtaksid,
        virkningstidspunkt = this.virkningstidspunkt,
        boforhold = this.boforhold,
        inntekter = this.inntekter?.tilOppdatereInntekterRequestV2(personidentBm),
    )
}

fun OppdatereInntekterRequest.tilOppdatereInntekterRequestV2(personidentBm: Personident): OppdatereInntekterRequestV2 {
    val inntekt = this.inntekter.orEmpty().map { i -> i.tilInntektDtoV2() }.toMutableSet()
    val barnetillegg = this.barnetillegg.orEmpty().map { bt -> bt.tilInntektDtoV2() }.toSet()
    val kontantstøtte = this.kontantstøtte.orEmpty().map { bt -> bt.tilInntektDtoV2() }.toSet()
    val utvidetBarnetrygd =
        this.utvidetbarnetrygd.orEmpty().map { ubt -> ubt.tilInntektDtoV2(personidentBm) }
            .toSet()

    return OppdatereInntekterRequestV2(
        inntekter = inntekt + barnetillegg + kontantstøtte + utvidetBarnetrygd,
        notat = this.notat,
    )
}

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
        opprinneligFom = this.opprinneligFom?.atDay(1),
        opprinneligTom = this.opprinneligTom?.atEndOfMonth(),
        ident = Personident(this.ident),
        gjelderBarn = null,
        kilde = if (this.fraGrunnlag == true) Kilde.OFFENTLIG else Kilde.MANUELL,
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
