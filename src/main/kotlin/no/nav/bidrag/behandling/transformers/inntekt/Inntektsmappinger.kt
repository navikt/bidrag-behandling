package no.nav.bidrag.behandling.transformers.inntekt

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.dto.v2.behandling.GrunnlagInntektEndringstype
import no.nav.bidrag.behandling.dto.v2.behandling.IkkeAktivInntektDto
import no.nav.bidrag.behandling.dto.v2.behandling.InntektspostEndringDto
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektspostDtoV2
import no.nav.bidrag.behandling.dto.v2.inntekt.OppdatereManuellInntekt
import no.nav.bidrag.behandling.transformers.behandling.mapTilInntektspostEndringer
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import java.math.BigDecimal
import java.time.LocalDateTime

fun OppdatereManuellInntekt.tilInntekt(inntekt: Inntekt): Inntekt {
    inntekt.type = this.type
    inntekt.belop = this.beløp
    inntekt.datoFom = this.datoFom
    inntekt.datoTom = this.datoTom
    inntekt.gjelderBarn = this.gjelderBarn?.verdi
    inntekt.kilde = Kilde.MANUELL
    inntekt.taMed = this.taMed
    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp,
                    inntektstype = this.inntektstype,
                    kode = this.type.toString(),
                ),
            )
    }
    return inntekt
}

fun OppdatereManuellInntekt.tilInntekt(behandling: Behandling): Inntekt {
    val inntekt =
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

    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp,
                    inntektstype = this.inntektstype,
                    kode = this.type.toString(),
                ),
            )
    }

    return inntekt
}

fun Set<Inntektspost>.tilInntektspostDtoV2() =
    this.map {
        InntektspostDtoV2(
            kode = it.kode,
            visningsnavn = finnVisningsnavn(it.kode),
            inntektstype = it.inntektstype,
            beløp = it.beløp.nærmesteHeltall,
        )
    }

fun SummertMånedsinntekt.tilInntektDtoV2(gjelder: String) =
    InntektDtoV2(
        id = -1,
        taMed = false,
        rapporteringstype = Inntektsrapportering.AINNTEKT,
        beløp = sumInntekt.nærmesteHeltall,
        ident = Personident(gjelder),
        kilde = Kilde.OFFENTLIG,
        inntektsposter =
            inntektPostListe.map {
                InntektspostDtoV2(
                    kode = it.kode,
                    visningsnavn = finnVisningsnavn(it.kode),
                    inntektstype = it.inntekstype,
                    beløp = it.beløp.nærmesteHeltall,
                )
            }.toSet(),
        inntektstyper = emptySet(),
        datoFom = gjelderÅrMåned.atDay(1),
        datoTom = gjelderÅrMåned.atEndOfMonth(),
        opprinneligFom = gjelderÅrMåned.atDay(1),
        opprinneligTom = gjelderÅrMåned.atEndOfMonth(),
        gjelderBarn = null,
    )

fun List<Inntekt>.tilInntektDtoV2() = this.map { it.tilInntektDtoV2() }

fun Inntekt.tilInntektDtoV2() =
    InntektDtoV2(
        id = this.id,
        taMed = this.taMed,
        rapporteringstype = this.type,
        beløp =
            maxOf(
                belop.nærmesteHeltall,
                BigDecimal.ZERO,
            ),
        // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        ident = Personident(this.ident),
        gjelderBarn = this.gjelderBarn?.let { it1 -> Personident(it1) },
        kilde = this.kilde,
        inntektsposter = this.inntektsposter.tilInntektspostDtoV2().toSet(),
        inntektstyper = this.inntektsposter.mapNotNull { it.inntektstype }.toSet(),
        opprinneligFom = this.opprinneligFom,
        opprinneligTom = this.opprinneligTom,
    )

fun OppdatereManuellInntekt.oppdatereEksisterendeInntekt(inntekt: Inntekt): Inntekt {
    inntekt.type = this.type
    inntekt.belop = this.beløp.nærmesteHeltall
    inntekt.datoFom = this.datoFom
    inntekt.datoTom = this.datoTom
    inntekt.gjelderBarn = this.gjelderBarn?.verdi
    inntekt.kilde = Kilde.MANUELL
    inntekt.taMed = this.taMed
    if (this.inntektstype != null) {
        inntekt.inntektsposter.removeAll(inntekt.inntektsposter)
        inntekt.inntektsposter.add(
            Inntektspost(
                inntekt = inntekt,
                beløp = this.beløp.nærmesteHeltall,
                inntektstype = this.inntektstype,
                kode = this.type.toString(),
            ),
        )
    }
    return inntekt
}

fun Inntekt.tilIkkeAktivInntektDto(
    endringstype: GrunnlagInntektEndringstype,
    innhentetTidspunkt: LocalDateTime,
) = IkkeAktivInntektDto(
    rapporteringstype = this.type,
    beløp =
        maxOf(
            this.belop.nærmesteHeltall,
            BigDecimal.ZERO,
        ),
    // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
    periode = this.opprinneligPeriode!!,
    ident = Personident(this.ident),
    gjelderBarn = gjelderBarn?.let { Personident(it) },
    endringstype = endringstype,
    innhentetTidspunkt = innhentetTidspunkt,
    originalId = id,
    inntektsposter =
        inntektsposter.map {
            InntektspostDtoV2(
                it.kode,
                finnVisningsnavn(it.kode),
                it.inntektstype,
                it.beløp.nærmesteHeltall,
            )
        }.toSet(),
)

fun SummertÅrsinntekt.tilIkkeAktivInntektDto(
    gjelderIdent: String,
    endringstype: GrunnlagInntektEndringstype,
    innhentetTidspunkt: LocalDateTime,
    eksisterendeInntekt: Inntekt? = null,
) = IkkeAktivInntektDto(
    rapporteringstype = this.inntektRapportering,
    // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
    beløp = maxOf(this.sumInntekt.nærmesteHeltall, BigDecimal.ZERO),
    periode = this.periode,
    ident = Personident(gjelderIdent),
    gjelderBarn = gjelderBarnPersonId?.let { Personident(it) },
    endringstype = endringstype,
    innhentetTidspunkt = innhentetTidspunkt,
    originalId = eksisterendeInntekt?.id,
    inntektsposter =
        inntektPostListe.map { it.toInntektpost() }.toSet(),
    inntektsposterSomErEndret =
        if (eksisterendeInntekt != null) {
            mapTilInntektspostEndringer(inntektPostListe.toSet(), eksisterendeInntekt.inntektsposter)
        } else {
            emptySet()
        },
)

fun Inntektspost.tilInntektspostEndring(endringstype: GrunnlagInntektEndringstype) =
    InntektspostEndringDto(
        kode,
        finnVisningsnavn(kode),
        inntektstype,
        beløp.nærmesteHeltall,
        endringstype,
    )

fun InntektPost.tilInntektspostEndring(endringstype: GrunnlagInntektEndringstype) =
    InntektspostEndringDto(
        kode,
        finnVisningsnavn(kode),
        inntekstype,
        beløp.nærmesteHeltall,
        endringstype,
    )

fun InntektPost.toInntektpost() =
    InntektspostDtoV2(
        kode,
        finnVisningsnavn(kode),
        inntekstype,
        beløp.nærmesteHeltall,
    )

fun OppdatereManuellInntekt.lagreSomNyInntekt(behandling: Behandling): Inntekt {
    val inntekt =
        Inntekt(
            type = this.type,
            belop = this.beløp.nærmesteHeltall,
            datoFom = this.datoFom,
            datoTom = this.datoTom,
            ident = this.ident.verdi,
            gjelderBarn = this.gjelderBarn?.verdi,
            kilde = Kilde.MANUELL,
            taMed = this.taMed,
            behandling = behandling,
        )

    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp.nærmesteHeltall,
                    inntektstype = this.inntektstype,
                    kode = this.type.toString(),
                ),
            )
    }

    behandling.inntekter.add(inntekt)

    return inntekt
}

fun opprettTransformerInntekterRequest(
    behandling: Behandling,
    innhentetGrunnlag: HentGrunnlagDto,
    rolleInhentetFor: Rolle,
) = TransformerInntekterRequest(
    ainntektHentetDato = innhentetGrunnlag.hentetTidspunkt.toLocalDate(),
    vedtakstidspunktOpprinneligeVedtak =
        behandling.opprinneligVedtakstidspunkt.map { it.toLocalDate() },
    ainntektsposter =
        innhentetGrunnlag.ainntektListe.flatMap {
            it.ainntektspostListe.tilAinntektsposter(
                rolleInhentetFor,
            )
        },
    barnetilleggsliste =
        innhentetGrunnlag.barnetilleggListe.filter {
            harBarnRolleIBehandling(
                it.barnPersonId,
                behandling,
            )
        }.tilBarnetillegg(
            rolleInhentetFor,
        ),
    kontantstøtteliste =
        innhentetGrunnlag.kontantstøtteListe.filter {
            harBarnRolleIBehandling(
                it.barnPersonId,
                behandling,
            )
        }.tilKontantstøtte(
            rolleInhentetFor,
        ),
    skattegrunnlagsliste =
        innhentetGrunnlag.skattegrunnlagListe.tilSkattegrunnlagForLigningsår(
            rolleInhentetFor,
        ),
    småbarnstilleggliste =
        innhentetGrunnlag.småbarnstilleggListe.tilSmåbarnstillegg(
            rolleInhentetFor,
        ),
    utvidetBarnetrygdliste =
        innhentetGrunnlag.utvidetBarnetrygdListe.tilUtvidetBarnetrygd(
            rolleInhentetFor,
        ),
)

private fun harBarnRolleIBehandling(
    personidentBarn: String,
    behandling: Behandling,
) = behandling.roller.filter { Rolletype.BARN == it.rolletype }.any { personidentBarn == it.ident }
