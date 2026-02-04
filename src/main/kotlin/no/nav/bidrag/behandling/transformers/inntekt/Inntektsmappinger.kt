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
import no.nav.bidrag.behandling.transformers.behandling.finnRolle
import no.nav.bidrag.behandling.transformers.behandling.mapTilInntektspostEndringer
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.erHistorisk
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.tilÅrsbeløp
import no.nav.bidrag.behandling.transformers.validerPerioder
import no.nav.bidrag.beregn.core.util.avrundetTilToDesimaler
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.commons.service.finnVisningsnavn
import no.nav.bidrag.domene.enums.diverse.InntektBeløpstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.inntekt.util.InntektUtil
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.response.InntektPost
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import no.nav.bidrag.transport.felles.ifTrue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

fun Inntekt.bestemDatoFomForOffentligInntekt() =
    skalAutomatiskSettePeriode().ifTrue {
        maxOf(
            opprinneligFom!!,
            behandling!!.virkningstidspunktEllerSøktFomDato,
        )
    }

fun Inntekt.bestemOpprinneligTomVisningsverdi() =
    opprinneligTom?.let { opprinneligTom ->
        if (this.kilde == Kilde.OFFENTLIG && eksplisitteYtelser.contains(this.type)) {
            val maxDate = maxOf(YearMonth.now().atEndOfMonth(), behandling!!.virkningstidspunktEllerSøktFomDato)
            if (opprinneligTom.plusMonths(1).isAfter(maxDate)) null else opprinneligTom
        } else {
            opprinneligTom
        }
    }

fun Inntekt.bestemDatoTomForOffentligInntekt() =
    skalAutomatiskSettePeriode().ifTrue {
        opprinneligTom?.let { tom ->
            val maxDate =
                maxOf(
                    YearMonth.now().atEndOfMonth(),
                    minOf(YearMonth.now().atEndOfMonth(), opphørsdato?.plusMonths(1)?.minusDays(1) ?: LocalDate.MAX),
                    behandling!!.virkningstidspunktEllerSøktFomDato,
                )
            if (tom.plusMonths(1).isAfter(maxDate)) {
                justerPeriodeTomOpphørsdato(opphørsdato)
            } else {
                justerPeriodeTomOpphørsdato(opphørsdato)
                    ?: tom
            }
        }
    }

fun Inntekt.skalAutomatiskSettePeriode(): Boolean =
    kilde == Kilde.OFFENTLIG && eksplisitteYtelser.contains(type) && erOpprinneligPeriodeInnenforVirkningstidspunktEllerOpphør()

fun Inntekt.erOpprinneligPeriodeInnenforVirkningstidspunktEllerOpphør(): Boolean =
    opprinneligFom?.let { fom ->
        if (beregnTilDato != null && fom > beregnTilDato) return@let false
        (opprinneligTom ?: LocalDate.MAX).let { tom ->
            behandling?.eldsteVirkningstidspunkt?.let { virkningstidspunkt ->
                val virkningstidspunktEllerStartenAvNesteMåned =
                    maxOf(YearMonth.now().atDay(1), virkningstidspunkt)
                if (fom.isAfter(virkningstidspunktEllerStartenAvNesteMåned) &&
                    tom.isAfter(virkningstidspunktEllerStartenAvNesteMåned)
                ) {
                    false
                } else {
                    virkningstidspunkt in fom..tom ||
                        virkningstidspunktEllerStartenAvNesteMåned >= fom &&
                        tom.isAfter(virkningstidspunkt)
                }
            }
        }
    } ?: false

fun Set<Inntektspost>.tilInntektspostDtoV2() =
    this
        .map { inntekt ->
            InntektspostDtoV2(
                kode = inntekt.kode,
                visningsnavn = finnVisningsnavn(inntekt.kode),
                inntektstype = inntekt.inntektstype,
                beløpstype = inntekt.beløpstype,
                beløp =
                    inntekt.beløp.nærmesteHeltall *
                        if (inntekt.inntekt?.type ==
                            Inntektsrapportering.KAPITALINNTEKT
                        ) {
                            InntektUtil.kapitalinntektFaktor(inntekt.kode)
                        } else {
                            BigDecimal.ONE
                        },
            )
        }.sortedByDescending { it.beløp }

fun SummertMånedsinntekt.tilInntektDtoV2(gjelder: Rolle) =
    InntektDtoV2(
        id = -1,
        taMed = false,
        rapporteringstype = Inntektsrapportering.AINNTEKT,
        beløp = sumInntekt,
        ident = Personident(gjelder.ident!!),
        gjelderRolleId = gjelder.id ?: -1,
        kilde = Kilde.OFFENTLIG,
        inntektsposter =
            inntektPostListe
                .map {
                    InntektspostDtoV2(
                        kode = it.kode,
                        visningsnavn = finnVisningsnavn(it.kode),
                        inntektstype = it.inntekstype,
                        beløp = InntektUtil.kapitalinntektFaktor(it.kode) * it.beløp,
                        beløpstype = InntektBeløpstype.ÅRSBELØP,
                    )
                }.sortedByDescending { it.beløp }
                .toSet(),
        inntektstyper = emptySet(),
        datoFom = gjelderÅrMåned.atDay(1),
        datoTom = gjelderÅrMåned.atEndOfMonth(),
        opprinneligFom = gjelderÅrMåned.atDay(1),
        opprinneligTom = gjelderÅrMåned.atEndOfMonth(),
        gjelderBarn = null,
        gjelderBarnId = null,
    )

fun List<Inntekt>.tilInntektDtoV2() = this.map { it.tilInntektDtoV2() }

fun Inntekt.tilInntektDtoV2() =
    InntektDtoV2(
        id = this.id,
        taMed = this.taMed,
        rapporteringstype = this.type,
        beløp = maxOf(belop.nærmesteHeltall, BigDecimal.ZERO),
        // Kapitalinntekt kan ha negativ verdi. Dette skal ikke vises i frontend
        datoFom = this.datoFom,
        datoTom = this.datoTom,
        ident = Personident(this.gjelderIdent),
        gjelderRolleId = this.gjelderRolle?.id,
        gjelderBarn = this.gjelderBarnIdent?.let { it1 -> Personident(it1) },
        gjelderBarnId = this.gjelderSøknadsbarn?.id,
        kilde = this.kilde,
        inntektsposter = this.inntektsposter.tilInntektspostDtoV2().toSet(),
        inntektstyper = this.inntektsposter.mapNotNull { it.inntektstype }.toSet(),
        opprinneligFom = this.opprinneligFom,
        opprinneligTom = bestemOpprinneligTomVisningsverdi(),
        historisk = erHistorisk(behandling!!.inntekter),
    )

fun OppdatereManuellInntekt.oppdatereEksisterendeInntekt(inntekt: Inntekt): Inntekt {
    val gjelderBarnRolle =
        this.gjelderBarnId?.let { inntekt.behandling!!.roller.find { it.id == this.gjelderBarnId } }
            ?: this.gjelderBarn?.let { inntekt.behandling!!.finnRolle(it.verdi) }
    inntekt.type = this.type
    inntekt.belop = this.beløp.tilÅrsbeløp(`beløpstype`).avrundetTilToDesimaler
    inntekt.datoFom = this.datoFom
    inntekt.datoTom = this.datoTom ?: justerPeriodeTomOpphørsdato(inntekt.opphørsdato)
    inntekt.gjelderBarn = this.gjelderBarn?.verdi ?: gjelderBarnRolle?.ident
    inntekt.gjelderBarnRolle = gjelderBarnRolle
    inntekt.kilde = Kilde.MANUELL
    inntekt.taMed = this.taMed
    if (this.inntektstype != null) {
        inntekt.inntektsposter.removeAll(inntekt.inntektsposter)
        inntekt.inntektsposter.add(
            Inntektspost(
                inntekt = inntekt,
                beløp = this.beløp.nærmesteHeltall,
                inntektstype = this.inntektstype,
                beløpstype = beløpstype,
                kode = this.type.toString(),
            ),
        )
    }
    inntekt.validerPerioder()
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
    ident = Personident(this.gjelderIdent),
    gjelderBarn = gjelderBarnIdent?.let { Personident(it) },
    endringstype = endringstype,
    innhentetTidspunkt = innhentetTidspunkt,
    originalId = id,
    inntektsposter =
        inntektsposter
            .map {
                InntektspostDtoV2(
                    it.kode,
                    finnVisningsnavn(it.kode),
                    it.inntektstype,
                    it.beløp.nærmesteHeltall,
                    it.beløpstype,
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
        InntektBeløpstype.ÅRSBELØP,
    )

fun OppdatereManuellInntekt.lagreSomNyInntekt(behandling: Behandling): Inntekt {
    val rolle =
        behandling.roller.find { it.id == this.gjelderId }
            ?: behandling.roller.find { it.ident == this.ident?.verdi }
    val gjelderBarnRolle =
        this.gjelderBarnId?.let { behandling.roller.find { it.id == this.gjelderBarnId } }
            ?: this.gjelderBarn?.let { behandling.finnRolle(it.verdi) }
    val inntekt =
        Inntekt(
            type = this.type,
            belop = this.beløp.nærmesteHeltall,
            datoFom = this.datoFom,
            datoTom = this.datoTom,
            ident = this.ident?.verdi ?: rolle?.ident,
            rolle = rolle,
            gjelderBarnRolle = gjelderBarnRolle,
            gjelderBarn = this.gjelderBarn?.verdi ?: gjelderBarnRolle?.ident,
            kilde = Kilde.MANUELL,
            taMed = this.taMed,
            behandling = behandling,
        )

    inntekt.datoTom = this.datoTom ?: justerPeriodeTomOpphørsdato(inntekt.opphørsdato)

    if (this.inntektstype != null) {
        inntekt.inntektsposter =
            mutableSetOf(
                Inntektspost(
                    inntekt = inntekt,
                    beløp = this.beløp.nærmesteHeltall,
                    inntektstype = this.inntektstype,
                    beløpstype = this.beløpstype,
                    kode = this.type.toString(),
                ),
            )
    }
    inntekt.validerPerioder()

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
        behandling.omgjøringsdetaljer?.omgjortVedtakstidspunktListe?.map { it.toLocalDate() } ?: emptyList(),
    ainntektsposter =
        innhentetGrunnlag.ainntektListe.flatMap {
            it.ainntektspostListe.tilAinntektsposter(
                rolleInhentetFor,
            )
        },
    barnetilleggsliste =
        innhentetGrunnlag.barnetilleggListe
            .filter {
                harBarnRolleIBehandling(
                    it.barnPersonId,
                    behandling,
                )
            }.filter { it.barnetilleggType !== Inntektstype.BARNETILLEGG_TILTAKSPENGER.name }
            .tilBarnetillegg(
                rolleInhentetFor,
            ),
    kontantstøtteliste =
        innhentetGrunnlag.kontantstøtteListe
            .filter {
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
