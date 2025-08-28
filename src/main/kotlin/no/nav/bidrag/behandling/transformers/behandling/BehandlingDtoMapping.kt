package no.nav.bidrag.behandling.transformers.behandling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Notat
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.datamodell.tilPersonident
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v1.behandling.RolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDetaljerDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsinnhentingsfeil
import no.nav.bidrag.behandling.dto.v2.behandling.KanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.dto.v2.behandling.SivilstandAktivGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.SjekkRolleDto
import no.nav.bidrag.behandling.dto.v2.behandling.StønadTilBarnetilsynAktiveGrunnlagDto
import no.nav.bidrag.behandling.dto.v2.behandling.innhentesForRolle
import no.nav.bidrag.behandling.dto.v2.inntekt.BeregnetInntekterDto
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.InntektValideringsfeilDto
import no.nav.bidrag.behandling.dto.v2.validering.VirkningstidspunktFeilDto
import no.nav.bidrag.behandling.service.NotatService
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.bestemRollerSomKanHaInntekter
import no.nav.bidrag.behandling.transformers.bestemRollerSomMåHaMinstEnInntekt
import no.nav.bidrag.behandling.transformers.ekskluderYtelserFørVirkningstidspunkt
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.finnCutoffDatoFom
import no.nav.bidrag.behandling.transformers.finnHullIPerioder
import no.nav.bidrag.behandling.transformers.finnOverlappendePerioderInntekt
import no.nav.bidrag.behandling.transformers.harUgyldigSluttperiode
import no.nav.bidrag.behandling.transformers.inntekstrapporteringerSomKreverGjelderBarn
import no.nav.bidrag.behandling.transformers.inntekt.tilInntektDtoV2
import no.nav.bidrag.behandling.transformers.kanSkriveVurderingAvSkolegangAlle
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.behandling.transformers.opphørSisteTilDato
import no.nav.bidrag.behandling.transformers.sorterEtterDato
import no.nav.bidrag.behandling.transformers.sorterEtterDatoOgBarn
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.tilSærbidragKategoriDto
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.behandling.transformers.årsinntekterSortert
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.beregn.core.util.sluttenAvForrigeMåned
import no.nav.bidrag.boforhold.dto.BoforholdResponseV2
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.Datoperiode
import no.nav.bidrag.domene.util.visningsnavn
import no.nav.bidrag.organisasjon.dto.SaksbehandlerDto
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.sivilstand.response.SivilstandBeregnet
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
import no.nav.bidrag.transport.felles.ifTrue
import java.time.LocalDate
import java.time.ZoneOffset
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

private val log = KotlinLogging.logger {}

fun Behandling.tilBehandlingDetaljerDtoV2() =
    BehandlingDetaljerDtoV2(
        id = id!!,
        type = tilType(),
        vedtakstype = vedtakstype,
        opprinneligVedtakstype = omgjøringsdetaljer?.opprinneligVedtakstype,
        stønadstype = stonadstype,
        engangsbeløptype = engangsbeloptype,
        erKlageEllerOmgjøring = erKlageEllerOmgjøring,
        opprettetTidspunkt = opprettetTidspunkt,
        erVedtakFattet = vedtaksid != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        søktAv = soknadFra,
        saksnummer = saksnummer,
        søknadsid = soknadsid!!,
        behandlerenhet = behandlerEnhet,
        roller =
            roller
                .map {
                    RolleDto(
                        it.id!!,
                        it.rolletype,
                        it.ident,
                        it.navn ?: hentPersonVisningsnavn(it.ident),
                        it.fødselsdato,
                    )
                }.toSet(),
        søknadRefId = omgjøringsdetaljer?.soknadRefId,
        vedtakRefId = omgjøringsdetaljer?.omgjørVedtakId,
        virkningstidspunkt = virkningstidspunkt,
        årsak = årsak,
        avslag = avslag,
        opprettetAv =
            SaksbehandlerDto(
                opprettetAv,
                opprettetAvNavn,
            ),
        kategori =
            when (engangsbeloptype) {
                Engangsbeløptype.SÆRBIDRAG -> tilSærbidragKategoriDto()
                else -> null
            },
    )

fun Rolle.tilDto() =
    RolleDto(
        id!!,
        rolletype,
        ident,
        navn ?: hentPersonVisningsnavn(ident),
        fødselsdato,
        this.harInnvilgetTilleggsstønad(),
    )

fun Rolle.harInnvilgetTilleggsstønad(): Boolean? {
    val tilleggsstønad =
        this.behandling.grunnlag
            .filter { Grunnlagsdatatype.TILLEGGSSTØNAD == it.type && !it.erBearbeidet }
            .filter { this == it.rolle }

    if (tilleggsstønad.isNotEmpty()) {
        return tilleggsstønad
            .maxBy { it.innhentet }
            .konvertereData<Set<TilleggsstønadGrunnlagDto>>()
            ?.firstOrNull()
            ?.harInnvilgetVedtak
    }
    return null
}

fun Map<Grunnlagsdatatype, GrunnlagFeilDto?>.tilGrunnlagsinnhentingsfeil(behandling: Behandling) =
    this
        .map { feil ->
            Grunnlagsinnhentingsfeil(
                rolle =
                    feil.value?.let { p -> behandling.roller.find { p.personId == it.ident }?.tilDto()!! }
                        ?: behandling.bidragsmottaker!!.tilDto(),
                feilmelding = feil.value?.feilmelding ?: "Uspesifisert feil oppstod ved innhenting av grunnlag",
                grunnlagsdatatype = feil.key,
                periode = feil.value?.periodeFra?.let { Datoperiode(feil.value?.periodeFra!!, feil.value?.periodeTil) },
            )
        }.toSet()

fun Grunnlag?.toSivilstand(): SivilstandAktivGrunnlagDto? {
    if (this == null) return null
    val harUgyldigStatus =
        behandling.sivilstand.any { it.sivilstand == Sivilstandskode.UKJENT } || behandling.sivilstand.isEmpty()
    val sortertGrunnlag = konvertereData<List<SivilstandGrunnlagDto>>()?.sortedBy { it.gyldigFom }
    val filtrertGrunnlag =
        harUgyldigStatus.ifTrue { sortertGrunnlag }
            ?: sortertGrunnlag?.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(behandling.virkningstidspunktEllerSøktFomDato)

    return SivilstandAktivGrunnlagDto(
        grunnlag = filtrertGrunnlag?.toSet() ?: emptySet(),
        innhentetTidspunkt = innhentet,
    )
}

fun Set<Grunnlag>.tilBarnetilsynAktiveGrunnlagDto(): StønadTilBarnetilsynAktiveGrunnlagDto? {
    if (this.isEmpty()) return null
    return StønadTilBarnetilsynAktiveGrunnlagDto(
        grunnlag =
            this
                .flatMap { it.konvertereData<Set<BarnetilsynGrunnlagDto>>() ?: emptySet() }
                .toSet()
                .groupBy { it.barnPersonId }
                .map { (personidentBarn, barnetilsyn) ->
                    Personident(personidentBarn) to barnetilsyn.toSet()
                }.toMap(),
        innhentetTidspunkt = first().innhentet,
    )
}

fun Behandling.tilInntektDtoV2(
    gjeldendeAktiveGrunnlagsdata: List<Grunnlag> = emptyList(),
    inkluderHistoriskeInntekter: Boolean = false,
) = InntekterDtoV2(
    barnetillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.BARNETILLEGG }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    utvidetBarnetrygd =
        inntekter
            .filter { it.type == Inntektsrapportering.UTVIDET_BARNETRYGD }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    kontantstøtte =
        inntekter
            .filter { it.type == Inntektsrapportering.KONTANTSTØTTE }
            .sorterEtterDatoOgBarn()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    småbarnstillegg =
        inntekter
            .filter { it.type == Inntektsrapportering.SMÅBARNSTILLEGG }
            .sorterEtterDato()
            .ekskluderYtelserFørVirkningstidspunkt()
            .tilInntektDtoV2()
            .toSet(),
    månedsinntekter =
        gjeldendeAktiveGrunnlagsdata
            .filter { it.type == Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER && it.erBearbeidet }
            .flatMap { grunnlag ->
                grunnlag.konvertereData<SummerteInntekter<SummertMånedsinntekt>>()?.inntekter?.map {
                    it.tilInntektDtoV2(
                        grunnlag.rolle.ident!!,
                    )
                } ?: emptyList()
            }.toSet(),
    årsinntekter =
        inntekter
            .årsinntekterSortert(inkluderHistoriskeInntekter = inkluderHistoriskeInntekter)
            .tilInntektDtoV2()
            .toSet(),
    beregnetInntekter =
        roller
            .map {
                BeregnetInntekterDto(
                    it.tilPersonident()!!,
                    it.rolletype,
                    hentBeregnetInntekterForRolle(it),
                )
            },
    begrunnelser =
        this.roller
            .mapNotNull { r ->
                val inntektsnotat = NotatService.henteInntektsnotat(this, r.id!!)
                inntektsnotat?.let {
                    BegrunnelseDto(
                        innhold = it,
                        gjelder = r.tilDto(),
                    )
                }
            }.toSet(),
    begrunnelserFraOpprinneligVedtak =
        this.roller
            .mapNotNull { r ->
                val inntektsnotat = NotatService.henteInntektsnotat(this, r.id!!, false)
                inntektsnotat.takeIfNotNullOrEmpty {
                    BegrunnelseDto(
                        innhold = it,
                        gjelder = r.tilDto(),
                    )
                }
            }.toSet(),
    valideringsfeil = hentInntekterValideringsfeil(),
)

fun Behandling.hentVirkningstidspunktValideringsfeil(): VirkningstidspunktFeilDto {
    val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
        erKlageEllerOmgjøring &&
            omgjøringsdetaljer?.opprinneligVirkningstidspunkt != null &&
            virkningstidspunkt?.isAfter(omgjøringsdetaljer!!.opprinneligVirkningstidspunkt) == true
    val begrunnelseVirkningstidspunkt = NotatService.henteNotatinnhold(this, NotatType.VIRKNINGSTIDSPUNKT)

    return VirkningstidspunktFeilDto(
        manglerÅrsakEllerAvslag = avslag == null && årsak == null,
        manglerVirkningstidspunkt = virkningstidspunkt == null,
        manglerVurderingAvSkolegang =
            if (kanSkriveVurderingAvSkolegangAlle()) {
                søknadsbarn.any {
                    NotatService
                        .henteNotatinnhold(
                            this,
                            rolle = it,
                            notattype = NotatType.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                        ).isEmpty()
                }
            } else {
                false
            },
        manglerOpphørsdato =
            if (stonadstype == Stønadstype.BIDRAG18AAR && avslag == null) {
                søknadsbarn.filter { it.opphørsdato == null }.map { it.tilDto() }
            } else {
                emptyList()
            },
        manglerBegrunnelse =
            if (vedtakstype == Vedtakstype.OPPHØR) {
                begrunnelseVirkningstidspunkt.isEmpty()
            } else {
                false
            },
        virkningstidspunktKanIkkeVæreSenereEnnOpprinnelig =
            if (erKlageEllerOmgjøring && erBidrag()) {
                false
            } else {
                erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt
            },
    )
}

fun Behandling.hentInntekterValideringsfeil(): InntektValideringsfeilDto =
    InntektValideringsfeilDto(
        årsinntekter =
            inntekter
                .mapValideringsfeilForÅrsinntekter(
                    virkningstidspunktEllerSøktFomDato,
                    roller,
                    tilType(),
                ).takeIf { it.isNotEmpty() },
        barnetillegg =
            inntekter
                .mapValideringsfeilForYtelseSomGjelderBarn(
                    Inntektsrapportering.BARNETILLEGG,
                    virkningstidspunktEllerSøktFomDato,
                    roller,
                ).takeIf { it.isNotEmpty() },
        småbarnstillegg =
            inntekter
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.SMÅBARNSTILLEGG,
                    virkningstidspunktEllerSøktFomDato,
                    roller,
                ).firstOrNull(),
        // Det er bare bidragsmottaker småbarnstillegg og utvidetbarnetrygd er relevant for. Antar derfor det alltid gjelder BM og velger derfor den første i listen
        utvidetBarnetrygd =
            inntekter
                .mapValideringsfeilForYtelse(
                    Inntektsrapportering.UTVIDET_BARNETRYGD,
                    virkningstidspunktEllerSøktFomDato,
                    roller,
                ).firstOrNull(),
        kontantstøtte =
            inntekter
                .mapValideringsfeilForYtelseSomGjelderBarn(
                    Inntektsrapportering.KONTANTSTØTTE,
                    virkningstidspunktEllerSøktFomDato,
                    roller,
                ).takeIf { it.isNotEmpty() },
    )

fun Set<Inntekt>.mapValideringsfeilForÅrsinntekter(
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    behandlingType: TypeBehandling = TypeBehandling.FORSKUDD,
): Set<InntektValideringsfeil> {
    val inntekterSomSkalSjekkes = filter { !eksplisitteYtelser.contains(it.type) }.filter { it.taMed }
    val rollerSomKreverMinstEnInntekt = bestemRollerSomMåHaMinstEnInntekt(behandlingType)
    return roller
        .filter { bestemRollerSomKanHaInntekter(behandlingType).contains(it.rolletype) }
        .map { rolle ->
            val opphørsdato = rolle.behandling.globalOpphørsdato
            val inntekterTaMed = inntekterSomSkalSjekkes.filter { it.ident == rolle.ident }

            if (inntekterTaMed.isEmpty() && (rollerSomKreverMinstEnInntekt.contains(rolle.rolletype))) {
                InntektValideringsfeil(
                    hullIPerioder = emptyList(),
                    overlappendePerioder = emptySet(),
                    fremtidigPeriode = false,
                    manglerPerioder = true,
                    rolle = rolle.tilDto(),
                )
            } else {
                val hullIPerioder =
                    if (rolle.rolletype == Rolletype.BARN) {
                        // Kan ha hull i perioder hvis det er barn
                        // Feks at barnet bare har inntekt fra sommerjobb
                        emptyList()
                    } else {
                        inntekterTaMed.finnHullIPerioder(virkningstidspunkt, opphørsdato)
                    }
                InntektValideringsfeil(
                    hullIPerioder = hullIPerioder,
                    overlappendePerioder = inntekterTaMed.finnOverlappendePerioderInntekt(),
                    fremtidigPeriode = inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
                    ugyldigSluttPeriode = inntekterTaMed.harUgyldigSluttperiode(opphørsdato),
                    manglerPerioder =
                        (rolle.rolletype != Rolletype.BARN)
                            .ifTrue { this.isEmpty() } == true,
                    rolle = rolle.tilDto(),
                    ingenLøpendePeriode =
                        if (opphørsdato == null ||
                            opphørsdato.opphørSisteTilDato().isAfter(LocalDate.now().sluttenAvForrigeMåned)
                        ) {
                            hullIPerioder.any { it.til == null }
                        } else {
                            false
                        },
                )
            }
        }.filter { it.harFeil }
        .toSet()
}

fun Set<Inntekt>.mapValideringsfeilForYtelse(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
    gjelderBarn: String? = null,
) = filter { it.taMed }
    .filter { it.type == type }
    .groupBy { it.ident }
    .map { (inntektGjelderIdent, inntekterTaMed) ->
        val gjelderRolle = roller.find { it.ident == inntektGjelderIdent }
        val gjelderIdent = gjelderRolle?.ident ?: inntektGjelderIdent
        InntektValideringsfeil(
            overlappendePerioder = inntekterTaMed.finnOverlappendePerioderInntekt(),
            fremtidigPeriode =
                inntekterTaMed.inneholderFremtidigPeriode(virkningstidspunkt),
            ugyldigSluttPeriode = inntekterTaMed.harUgyldigSluttperiode(inntekterTaMed.firstOrNull()?.opphørsdato),
            ident = gjelderIdent,
            rolle = gjelderRolle?.tilDto(),
            gjelderBarn = gjelderBarn,
            erYtelse = true,
        ).takeIf { it.harFeil }
    }

fun Set<Inntekt>.mapValideringsfeilForYtelseSomGjelderBarn(
    type: Inntektsrapportering,
    virkningstidspunkt: LocalDate,
    roller: Set<Rolle>,
) = filter { inntekstrapporteringerSomKreverGjelderBarn.contains(type) }
    .groupBy { it.gjelderBarn }
    .flatMap { (gjelderBarn, inntekter) ->
        inntekter.toSet().mapValideringsfeilForYtelse(
            type,
            virkningstidspunkt,
            roller,
            gjelderBarn,
        )
    }.filterNotNull()
    .toSet()

fun List<Inntekt>.inneholderFremtidigPeriode(virkningstidspunkt: LocalDate) =
    any {
        it.datoFom!!.isAfter(maxOf(virkningstidspunkt.withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
    }

fun Behandling.hentBeregnetInntekterForRolle(rolle: Rolle) =
    BeregnApi()
        .beregnInntekt(tilInntektberegningDto(rolle))
        .inntektPerBarnListe
        .sortedBy {
            it.inntektGjelderBarnIdent?.verdi
        }.map {
            it.copy(
                summertInntektListe =
                    it.summertInntektListe.map { delberegning ->
                        delberegning.copy(
                            barnetillegg = delberegning.barnetillegg?.nærmesteHeltall,
                            småbarnstillegg = delberegning.småbarnstillegg?.nærmesteHeltall,
                            kontantstøtte = delberegning.kontantstøtte?.nærmesteHeltall,
                            utvidetBarnetrygd = delberegning.utvidetBarnetrygd?.nærmesteHeltall,
                            skattepliktigInntekt = delberegning.skattepliktigInntekt?.nærmesteHeltall,
                            totalinntekt = delberegning.totalinntekt.nærmesteHeltall,
                        )
                    },
            )
        }

fun Behandling.tilReferanseId() = "bidrag_behandling_${id}_${opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)}"

fun Behandling.tilNotat(
    notattype: Notattype,
    tekst: String,
    rolleVedInntekt: Rolle? = null,
    delAvBehandling: Boolean = true,
): Notat {
    val gjelder = this.henteRolleForNotat(notattype, rolleVedInntekt)
    return Notat(behandling = this, rolle = gjelder, type = notattype, innhold = tekst, erDelAvBehandlingen = delAvBehandling)
}

fun Behandling.henteRolleForNotat(
    notattype: Notattype,
    forRolle: Rolle?,
): Rolle =
    when (notattype) {
        Notattype.BOFORHOLD -> Grunnlagsdatatype.BOFORHOLD.innhentesForRolle(this)!!
        Notattype.UTGIFTER -> this.bidragsmottaker!!
        Notattype.VIRKNINGSTIDSPUNKT -> forRolle ?: this.bidragsmottaker!!
        Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG -> forRolle ?: this.bidragsmottaker!!
        Notattype.INNTEKT -> {
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragsmottaker!!
            } else {
                forRolle
            }
        }

        Notattype.UNDERHOLDSKOSTNAD ->
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragsmottaker!!
            } else {
                forRolle
            }

        Notattype.SAMVÆR -> forRolle!!
        Notattype.PRIVAT_AVTALE ->
            if (forRolle == null) {
                log.warn { "Notattype $notattype krever spesifisering av hvilken rolle notatet gjelder." }
                this.bidragspliktig!!
            } else {
                forRolle
            }
    }

fun Behandling.notatTittel(): String {
    val prefiks =
        when (stonadstype) {
            Stønadstype.FORSKUDD -> "Bidragsforskudd"
            Stønadstype.BIDRAG -> "Barnebidrag"
            Stønadstype.BIDRAG18AAR -> "Barnebidrag 18 år"
            Stønadstype.EKTEFELLEBIDRAG -> "Ektefellebidrag"
            Stønadstype.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
            Stønadstype.MOTREGNING -> "Motregning"
            else ->
                when (engangsbeloptype) {
                    Engangsbeløptype.SÆRBIDRAG, Engangsbeløptype.SÆRTILSKUDD, Engangsbeløptype.SAERTILSKUDD ->
                        "Særbidrag ${kategoriTilTittel()}".trim()
                    Engangsbeløptype.DIREKTE_OPPGJØR, Engangsbeløptype.DIREKTE_OPPGJØR -> "Direkte oppgjør"
                    Engangsbeløptype.ETTERGIVELSE -> "Ettergivelse"
                    Engangsbeløptype.ETTERGIVELSE_TILBAKEKREVING -> "Ettergivelse tilbakekreving"
                    Engangsbeløptype.GEBYR_MOTTAKER -> "Gebyr"
                    Engangsbeløptype.GEBYR_SKYLDNER -> "Gebyr"
                    Engangsbeløptype.TILBAKEKREVING -> "Tilbakekreving"
                    else -> null
                }
        }
    return "${prefiks?.let { "$prefiks, " }}Saksbehandlingsnotat"
}

fun Behandling.kategoriTilTittel() =
    if (engangsbeloptype == Engangsbeløptype.SÆRBIDRAG) {
        if (særbidragKategori == Særbidragskategori.ANNET) {
            kategoriBeskrivelse
        } else {
            særbidragKategori.visningsnavn.intern.lowercase()
        }
    } else {
        ""
    }

fun Set<BarnetilsynGrunnlagDto>.filtrerePerioderEtterVirkningstidspunkt(virkningstidspunkt: LocalDate): Set<BarnetilsynGrunnlagDto> =
    groupBy { it.barnPersonId }
        .flatMap { (_, perioder) ->
            val perioderFiltrert =
                perioder.sortedBy { it.periodeFra }.slice(
                    perioder
                        .map { it.periodeFra }
                        .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, null),
                )
            val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, null)
            perioderFiltrert.map { periode ->
                periode
                    .takeIf { it == perioderFiltrert.first() }
                    ?.copy(periodeFra = maxOf(periode.periodeFra, cutoffPeriodeFom)) ?: periode
            }
        }.toSet()

fun List<BoforholdResponseV2>.filtrerPerioderEtterVirkningstidspunktForBMsBoforhold(
    virkningstidspunkt: LocalDate,
): List<BoforholdResponseV2> =
    groupBy { it.gjelderPersonId }.flatMap { (_, perioder) ->
        val perioderFiltrert =
            perioder.sortedBy { it.periodeFom }.slice(
                perioder
                    .map { it.periodeFom }
                    .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, null),
            )
        val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, null)
        perioderFiltrert.map { periode ->
            periode
                .takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }

fun List<BoforholdResponseV2>.filtrerPerioderEtterVirkningstidspunkt(
    husstandsmedlemListe: Set<Husstandsmedlem>,
    virkningstidspunkt: LocalDate,
): List<BoforholdResponseV2> {
    return groupBy { it.gjelderPersonId }.flatMap { (barnId, perioder) ->
        val barn =
            husstandsmedlemListe.find { it.ident == barnId }
                ?: return@flatMap perioder
        val perioderFiltrert =
            perioder.sortedBy { it.periodeFom }.slice(
                perioder
                    .map { it.periodeFom }
                    .hentIndekserEtterVirkningstidspunkt(virkningstidspunkt, barn.fødselsdato),
            )
        val cutoffPeriodeFom = finnCutoffDatoFom(virkningstidspunkt, barn.fødselsdato)
        perioderFiltrert.map { periode ->
            periode
                .takeIf { it == perioderFiltrert.first() }
                ?.copy(periodeFom = maxOf(periode.periodeFom, cutoffPeriodeFom)) ?: periode
        }
    }
}

fun List<SivilstandGrunnlagDto>.filtrerSivilstandGrunnlagEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
): List<SivilstandGrunnlagDto> {
    val filtrertGrunnlag =
        sortedBy { it.gyldigFom }.slice(map { it.gyldigFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))
    return filtrertGrunnlag.ifEmpty {
        listOf(sortedBy { it.gyldigFom }.last())
    }
}

fun List<LocalDate?>.hentIndekserEtterVirkningstidspunkt(
    virkningstidspunkt: LocalDate,
    fødselsdato: LocalDate? = null,
): List<Int> {
    val kanIkkeVæreTidligereEnnDato = finnCutoffDatoFom(virkningstidspunkt, fødselsdato)
    val datoerSortert = sortedBy { it }

    return datoerSortert.mapIndexedNotNull { index, dato ->
        index.takeIf {
            if (dato == null) return@takeIf true
            val erEtterVirkningstidspunkt = dato >= kanIkkeVæreTidligereEnnDato
            if (!erEtterVirkningstidspunkt) {
                val nesteDato = datoerSortert.drop(index + 1).firstOrNull()
                nesteDato == null || nesteDato > kanIkkeVæreTidligereEnnDato
            } else {
                true
            }
        }
    }
}

fun SivilstandBeregnet.filtrerSivilstandBeregnetEtterVirkningstidspunktV1(virkningstidspunkt: LocalDate): SivilstandBeregnet =
    copy(
        sivilstandListe =
            sivilstandListe.sortedBy { it.periodeFom }.slice(
                sivilstandListe
                    .map {
                        it.periodeFom
                    }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt),
            ),
    )

fun List<Sivilstand>.filtrerSivilstandBeregnetEtterVirkningstidspunktV2(virkningstidspunkt: LocalDate): List<Sivilstand> =
    sortedBy {
        it.periodeFom
    }.slice(map { it.periodeFom }.hentIndekserEtterVirkningstidspunkt(virkningstidspunkt))

fun List<Grunnlag>.hentAlleBearbeidaBoforholdTilBMSøknadsbarn(virkniningstidspunkt: LocalDate) =
    asSequence()
        .filter { it.type == Grunnlagsdatatype.BOFORHOLD_BM_SØKNADSBARN && it.erBearbeidet }
        .mapNotNull { it.konvertereData<List<BoforholdResponseV2>>() }
        .flatten()
        .distinct()
        .toList()
        .filtrerPerioderEtterVirkningstidspunktForBMsBoforhold(virkniningstidspunkt)
        .sortedBy { it.periodeFom }

fun List<Grunnlag>.hentAlleBearbeidaBoforhold(
    virkniningstidspunkt: LocalDate,
    husstandsmedlem: Set<Husstandsmedlem>,
    rolle: Rolle,
) = asSequence()
    .filter { (it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BOFORHOLD && it.erBearbeidet }
    .mapNotNull { it.konvertereData<List<BoforholdResponseV2>>() }
    .flatten()
    .distinct()
    .toList()
    .filtrerPerioderEtterVirkningstidspunkt(
        husstandsmedlem,
        virkniningstidspunkt,
    ).sortedBy { it.periodeFom }

fun Set<Grunnlag>.hentAlleBearbeidaBarnetilsyn(
    virkniningstidspunkt: LocalDate,
    rolle: Rolle,
) = asSequence()
    .filter { (it.rolle.id == rolle.id) && it.type == Grunnlagsdatatype.BARNETILSYN && it.erBearbeidet }
    .mapNotNull { it.konvertereData<Set<BarnetilsynGrunnlagDto>>() }
    .flatten()
    .distinct()
    .toSet()
    .filtrerePerioderEtterVirkningstidspunkt(
        virkniningstidspunkt,
    ).sortedBy { it.periodeFra }
    .toSet()

fun Behandling.tilKanBehandlesINyLøsningRequest() =
    KanBehandlesINyLøsningRequest(
        engangsbeløpstype = engangsbeloptype,
        stønadstype = stonadstype,
        saksnummer = saksnummer,
        vedtakstype = vedtakstype,
        søknadstype = søknadstype,
        harReferanseTilAnnenBehandling = omgjøringsdetaljer != null,
        søktFomDato = søktFomDato,
        mottattdato = mottattdato,
        roller =
            roller.map {
                SjekkRolleDto(
                    rolletype = it.rolletype,
                    ident = Personident(it.ident!!),
                    erUkjent = false,
                )
            },
    )
