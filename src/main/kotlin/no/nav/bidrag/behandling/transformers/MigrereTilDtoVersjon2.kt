package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
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
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import java.math.BigDecimal

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
    val rapporteringstyperSomIkkeSkalInkluderesIInntekt =
        setOf(
            Inntektsrapportering.BARNETILLEGG,
            Inntektsrapportering.KONTANTSTØTTE,
            Inntektsrapportering.SMÅBARNSTILLEGG,
            Inntektsrapportering.UTVIDET_BARNETRYGD,
        )

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
        InntektPost(kode = it.kode, visningsnavn = it.visningsnavn, beløp = it.beløp ?: BigDecimal.ZERO)
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
    val barnetillegg = this.barnetillegg.orEmpty().map { bt -> bt.tilInntektDtoV2() }.toSet()
    val kontantstøtte = this.kontantstøtte.orEmpty().map { bt -> bt.tilInntektDtoV2() }.toMutableSet()
    val utvidetBarnetrygd =
        this.utvidetbarnetrygd.orEmpty().map { ubt -> ubt.tilInntektDtoV2(personidentBm) }
            .toMutableSet()

    return OppdatereInntekterRequestV2(
        inntekter = barnetillegg + kontantstøtte + utvidetBarnetrygd,
        notat = this.notat,
    )
}

fun BarnetilleggDto.tilInntektDtoV2(): InntektDtoV2 =
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

fun KontantstøtteDto.tilInntektDtoV2(): InntektDtoV2 =
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

fun UtvidetBarnetrygdDto.tilInntektDtoV2(personidentBm: Personident): InntektDtoV2 =
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

fun Set<BarnetilleggDto>.tilInntekt(behandling: Behandling): List<Inntekt> =
    this.map {
        Inntekt(
            ident = it.ident,
            gjelderBarn = it.gjelderBarn,
            belop = it.barnetillegg,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            behandling = behandling,
            // TODO: Endre til Inntektsrapportering.BARNETILLEGG når denne er på plass
            inntektsrapportering = Inntektsrapportering.BARNETILLEGG,
            // TODO: Hente fra DTO når spesifisert
            kilde = Kilde.MANUELL,
            taMed = true,
        )
    }

fun Set<UtvidetBarnetrygdDto>.tilInntekt(behandling: Behandling) =
    this.map {
        Inntekt(
            ident = behandling.roller.filter { r -> r.rolletype == Rolletype.BIDRAGSMOTTAKER }.first().ident!!,
            belop = it.beløp,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            behandling = behandling,
            inntektsrapportering = Inntektsrapportering.UTVIDET_BARNETRYGD,
            // TODO: Hente fra DTO når spesifisert
            kilde = Kilde.MANUELL,
            taMed = true,
        )
    }.toMutableSet()

fun Set<Inntekt>.tilBarnetilleggDto() =
    this.map {
        BarnetilleggDto(
            it.id,
            barnetillegg = it.belop,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            ident = it.ident,
            gjelderBarn = it.gjelderBarn!!,
        )
    }.toSet()

fun Set<Inntekt>.tilUtvidetBarnetrygd() =
    this.map {
        UtvidetBarnetrygdDto(
            it.id,
            beløp = it.belop,
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            deltBosted = false,
        )
    }.toSet()
