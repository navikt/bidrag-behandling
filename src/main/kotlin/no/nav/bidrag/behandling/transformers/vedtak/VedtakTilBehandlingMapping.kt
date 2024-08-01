package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Inntektspost
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.tilTypeFelles
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstandRequest
import no.nav.bidrag.behandling.transformers.byggResultatSærbidragsberegning
import no.nav.bidrag.behandling.transformers.finnAntallBarnIHusstanden
import no.nav.bidrag.behandling.transformers.finnSivilstandForPeriode
import no.nav.bidrag.behandling.transformers.finnTotalInntektForRolle
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.særbidragskategori
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftDirekteBetalt
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftsposter
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.behandling.vedtak.response.typeBehandling
import no.nav.bidrag.transport.behandling.vedtak.response.virkningstidspunkt
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

fun manglerPersonGrunnlag(referanse: Grunnlagsreferanse?): Nothing =
    vedtakmappingFeilet(
        "Mangler person med referanse $referanse i grunnlagslisten",
    )

val grunnlagstyperRolle =
    listOf(
        Grunnlagstype.PERSON_SØKNADSBARN,
        Grunnlagstype.PERSON_BIDRAGSMOTTAKER,
        Grunnlagstype.PERSON_BIDRAGSPLIKTIG,
    )
val inntektsrapporteringSomKreverBarn =
    listOf(
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.KONTANTSTØTTE,
    )

fun VedtakDto.tilBeregningResultat(): List<ResultatBeregningBarnDto> =
    stønadsendringListe.map { stønadsendring ->
        val barnIdent = stønadsendring.kravhaver
        val barn =
            grunnlagListe.hentPerson(barnIdent.verdi)?.innholdTilObjekt<Person>()
                ?: manglerPersonGrunnlag(barnIdent.verdi)
        ResultatBeregningBarnDto(
            barn =
                ResultatRolle(
                    barn.ident,
                    barn.navn ?: hentPersonVisningsnavn(barn.ident?.verdi)!!,
                    barn.fødselsdato,
                ),
            perioder =
                stønadsendring.periodeListe.map {
                    ResultatBeregningBarnDto.ResultatPeriodeDto(
                        periode = it.periode,
                        resultatKode = Resultatkode.fraKode(it.resultatkode)!!,
                        regel = "",
                        beløp = it.beløp ?: BigDecimal.ZERO,
                        sivilstand = grunnlagListe.finnSivilstandForPeriode(it.grunnlagReferanseListe),
                        inntekt = grunnlagListe.finnTotalInntektForRolle(it.grunnlagReferanseListe),
                        antallBarnIHusstanden = grunnlagListe.finnAntallBarnIHusstanden(it.grunnlagReferanseListe).toInt(),
                    )
                },
        )
    }

fun VedtakDto.tilBeregningResultatSærbidrag(): ResultatSærbidragsberegningDto? =
    engangsbeløpListe.firstOrNull()?.let { engangsbeløp ->
        val behandling = tilBehandling(1)
        grunnlagListe.byggResultatSærbidragsberegning(
            behandling.virkningstidspunkt!!,
            engangsbeløp.beløp,
            Resultatkode.fraKode(engangsbeløp.resultatkode)!!,
            engangsbeløp.grunnlagReferanseListe,
            behandling.utgift?.tilBeregningDto() ?: UtgiftBeregningDto(),
        )
    }

fun VedtakDto.tilBehandling(
    vedtakId: Long,
    lesemodus: Boolean = true,
    vedtakType: Vedtakstype? = null,
    mottattdato: LocalDate? = null,
    søktFomDato: LocalDate? = null,
    soknadFra: SøktAvType? = null,
    søknadRefId: Long? = null,
    søknadId: Long? = null,
    enhet: String? = null,
    opprinneligVedtakstidspunkt: Set<LocalDateTime> = emptySet(),
): Behandling {
    val opprettetAv =
        if (lesemodus) {
            this.opprettetAv
        } else {
            TokenUtils.hentSaksbehandlerIdent()
                ?: TokenUtils.hentApplikasjonsnavn()!!
        }
    val opprettetAvNavn =
        if (lesemodus) {
            this.opprettetAvNavn
        } else {
            TokenUtils
                .hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
        }
    val erKlage = søknadRefId != null
    val behandling =
        Behandling(
            id = if (lesemodus) 1 else null,
            vedtakstype = vedtakType ?: type,
            virkningstidspunkt = virkningstidspunkt ?: hentSøknad().søktFraDato,
            kategori = grunnlagListe.særbidragskategori?.kategori?.name,
            kategoriBeskrivelse = grunnlagListe.særbidragskategori?.beskrivelse,
            opprinneligVirkningstidspunkt =
                virkningstidspunkt
                    ?: hentSøknad().søktFraDato,
            opprinneligVedtakstidspunkt = opprinneligVedtakstidspunkt.toMutableSet(),
            innkrevingstype =
                this.stønadsendringListe.firstOrNull()?.innkreving
                    ?: this.engangsbeløpListe.firstOrNull()?.innkreving
                    ?: Innkrevingstype.MED_INNKREVING,
            årsak = hentVirkningstidspunkt()?.årsak,
            avslag = avslagskode(),
            klageMottattdato = if (!lesemodus) mottattdato else null,
            søktFomDato = søktFomDato ?: hentSøknad().søktFraDato,
            soknadFra = soknadFra ?: hentSøknad().søktAv,
            mottattdato =
                when (typeBehandling) {
                    no.nav.bidrag.domene.enums.behandling.TypeBehandling.SÆRBIDRAG -> hentSøknad().mottattDato
                    else -> mottattdato ?: hentSøknad().mottattDato
                },
            // TODO: Er dette riktig? Hva skjer hvis det finnes flere stønadsendringer/engangsbeløp? Fungerer for Forskudd men todo fram fremtiden
            stonadstype = stønadsendringListe.firstOrNull()?.type,
            engangsbeloptype = engangsbeløpListe.firstOrNull()?.type,
            vedtaksid = null,
            soknadRefId = søknadRefId,
            refVedtaksid = vedtakId,
            behandlerEnhet = enhet ?: enhetsnummer?.verdi!!,
            opprettetAv = opprettetAv,
            opprettetAvNavn = opprettetAvNavn,
            kildeapplikasjon = if (lesemodus) kildeapplikasjon else TokenUtils.hentApplikasjonsnavn()!!,
            datoTom = null,
            saksnummer = saksnummer!!,
            soknadsid = søknadId ?: this.søknadId!!,
            boforholdsbegrunnelseKunINotat = notatMedType(NotatGrunnlag.NotatType.BOFORHOLD, false),
            utgiftsbegrunnelseKunINotat = notatMedType(NotatGrunnlag.NotatType.UTGIFTER, false),
            boforholdsbegrunnelseIVedtakOgNotat =
                notatMedType(
                    NotatGrunnlag.NotatType.BOFORHOLD,
                    true,
                ),
            virkningstidspunktbegrunnelseKunINotat =
                notatMedType(
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    false,
                ),
            virkningstidspunktsbegrunnelseIVedtakOgNotat =
                notatMedType(
                    NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT,
                    true,
                ),
            inntektsbegrunnelseIVedtakOgNotat = notatMedType(NotatGrunnlag.NotatType.INNTEKT, true),
            inntektsbegrunnelseKunINotat = notatMedType(NotatGrunnlag.NotatType.INNTEKT, false),
        )
    behandling.roller = grunnlagListe.mapRoller(behandling, lesemodus)
    behandling.inntekter = grunnlagListe.mapInntekter(behandling, lesemodus)
    behandling.husstandsmedlem = grunnlagListe.mapHusstandsmedlem(behandling)
    behandling.sivilstand = grunnlagListe.mapSivilstand(behandling, lesemodus)
    behandling.utgift = grunnlagListe.mapUtgifter(behandling, lesemodus)
    behandling.grunnlag = grunnlagListe.mapGrunnlag(behandling, lesemodus)

    return behandling
}

private fun List<GrunnlagDto>.mapGrunnlag(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Grunnlag> =
    (
        hentGrunnlagIkkeInntekt(behandling, lesemodus) +
            hentGrunnlagInntekt(
                behandling,
                lesemodus,
            ) + hentInnntekterBearbeidet(behandling, lesemodus)
    ).toMutableSet()

private fun List<GrunnlagDto>.mapRoller(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Rolle> =
    filter { grunnlagstyperRolle.contains(it.type) }
        .mapIndexed { i, it -> it.tilRolle(behandling, if (lesemodus) i.toLong() else null) }
        .toMutableSet()

private fun List<GrunnlagDto>.mapHusstandsmedlem(behandling: Behandling): MutableSet<Husstandsmedlem> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.BOSTATUS_PERIODE)
        .groupBy { it.gjelderReferanse }
        .map {
            it.value.tilHusstandsmedlem(it.key!!, behandling, this)
        }.toMutableSet()

private fun List<GrunnlagDto>.mapSivilstand(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Sivilstand> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SIVILSTAND_PERIODE)
        .mapIndexed { i, it ->
            it.tilSivilstand(behandling, if (lesemodus) i.toLong() else null)
        }.toMutableSet()

private fun List<GrunnlagDto>.mapUtgifter(
    behandling: Behandling,
    lesemodus: Boolean,
): Utgift? {
    if (behandling.tilType() !== TypeBehandling.SÆRBIDRAG || behandling.avslag != null) return null
    val utgift = Utgift(behandling, beløpDirekteBetaltAvBp = utgiftDirekteBetalt!!.beløpDirekteBetalt)
    utgift.utgiftsposter =
        utgiftsposter
            .mapIndexed { index, it ->
                Utgiftspost(
                    id = if (lesemodus) index.toLong() else null,
                    utgift = utgift,
                    dato = it.dato,
                    type = it.type,
                    godkjentBeløp = it.godkjentBeløp,
                    kravbeløp = it.kravbeløp,
                    begrunnelse = it.begrunnelse,
                    betaltAvBp = it.betaltAvBp,
                )
            }.toMutableSet()

    return utgift
}

private fun List<GrunnlagDto>.mapInntekter(
    behandling: Behandling,
    lesemodus: Boolean,
): MutableSet<Inntekt> {
    val inntekter =
        filtrerBasertPåEgenReferanse(Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE)
            .mapIndexed { i, it ->
                it.tilInntekt(
                    behandling,
                    this,
                    if (lesemodus) i.toLong() else null,
                )
            }.toMutableSet()
    val erForskuddOmgjøring =
        behandling.soknadFra == SøktAvType.NAV_BIDRAG && behandling.vedtakstype == Vedtakstype.ENDRING
    if (!lesemodus && !erForskuddOmgjøring) {
        inntekter.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }?.let { originalInntekt ->
            originalInntekt
                .copy(
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_12MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                ).run {
                    inntekter.add(this)
                    originalInntekt.taMed = false
                    originalInntekt.datoFom = null
                    originalInntekt.datoTom = null
                }
        }

        inntekter.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_3MND }?.let { originalInntekt ->
            originalInntekt
                .copy(
                    type = Inntektsrapportering.AINNTEKT_BEREGNET_3MND_FRA_OPPRINNELIG_VEDTAKSTIDSPUNKT,
                ).run {
                    inntekter.add(this)
                    originalInntekt.taMed = false
                    originalInntekt.datoFom = null
                    originalInntekt.datoTom = null
                }
        }
    }
    val inntekterBeregnet =
        inntekter
            .filter { ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt.contains(it.type) }
            .sortedByDescending { it.taMed }
            .distinctBy { listOfNotNull(it.opprinneligFom, it.opprinneligTom, it.type, it.gjelderBarn, it.ident) }
    val andreInntekter = inntekter.filter { !ainntekt12Og3MånederFraOpprinneligVedtakstidspunkt.contains(it.type) }
    return (andreInntekter + inntekterBeregnet).toMutableSet()
}

fun List<GrunnlagDto>.innhentetTidspunkt(grunnlagstype: Grunnlagstype) =
    filtrerBasertPåEgenReferanse(grunnlagstype)
        .firstOrNull()
        ?.innhold
        ?.get("hentetTidspunkt")
        ?.let {
            commonObjectmapper.treeToValue(it, LocalDateTime::class.java)
        } ?: LocalDateTime.now()

fun List<GrunnlagDto>.hentGrunnlagIkkeInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
) = listOf(
    hentGrunnlagArbeidsforhold()
        .groupBy { it.partPersonId }
        .map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.ARBEIDSFORHOLD,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.INNHENTET_ARBEIDSFORHOLD),
                lesemodus,
            )
        },
    hentInnhentetSivilstand()
        .groupBy { it.personId }
        .flatMap { (gjelderIdent, grunnlag) ->
            val sivilstandPeriodisert =
                SivilstandApi.beregnV2(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    grunnlag.toSet().tilSivilstandRequest(
                        emptySet(),
                        behandling.bidragsmottaker!!.fødselsdato,
                    ),
                )

            listOf(
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SIVILSTAND,
                    grunnlag,
                    gjelderIdent!!,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                    lesemodus,
                ),
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SIVILSTAND,
                    sivilstandPeriodisert,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_SIVILSTAND),
                    lesemodus,
                    true,
                ),
            )
        },
    hentInnhentetHusstandsmedlem()
        .groupBy { it.partPersonId }
        .flatMap { (innhentetForIdent, grunnlag) ->

            val boforholdPeriodisert =
                BoforholdApi.beregnBoforholdBarnV3(
                    behandling.virkningstidspunktEllerSøktFomDato,
                    behandling.tilTypeFelles(),
                    grunnlag.tilBoforholdBarnRequest(behandling),
                )
            listOf(
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BOFORHOLD,
                    grunnlag,
                    innhentetForIdent!!,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                    lesemodus,
                ),
            ) +
                boforholdPeriodisert
                    .filter { it.gjelderPersonId != null }
                    .groupBy { it.gjelderPersonId }
                    .map {
                        behandling.opprettGrunnlag(
                            Grunnlagsdatatype.BOFORHOLD,
                            it.value,
                            grunnlag.firstOrNull()?.partPersonId!!,
                            innhentetTidspunkt(Grunnlagstype.INNHENTET_HUSSTANDSMEDLEM),
                            lesemodus,
                            true,
                            gjelder = it.key!!,
                        )
                    }
            // TODO: Boforhold andre voksne i husstand
        },
).flatten()

private fun List<GrunnlagDto>.hentGrunnlagInntekt(
    behandling: Behandling,
    lesemodus: Boolean,
): List<Grunnlag> =
    listOf(
        hentBeregnetInntekt().entries.map { (gjelderIdent, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER,
                grunnlag,
                gjelderIdent,
                innhentetTidspunkt(Grunnlagstype.BEREGNET_INNTEKT),
                lesemodus,
                erBearbeidet = true,
            )
        },
        hentBarnetillegListe()
            .groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILLEGG),
                    lesemodus,
                )
            },
        hentUtvidetbarnetrygdListe()
            .groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_UTVIDETBARNETRYGD),
                    lesemodus,
                )
            },
        hentSmåbarnstilleggListe()
            .groupBy { it.personId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SMÅBARNSTILLEGG,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SMÅBARNSTILLEGG),
                    lesemodus,
                )
            },
        hentBarnetilsynListe()
            .groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.BARNETILSYN,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_BARNETILSYN),
                    lesemodus,
                )
            },
        hentKontantstøtteListe()
            .groupBy { it.partPersonId }
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.KONTANTSTØTTE,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_KONTANTSTØTTE),
                    lesemodus,
                )
            },
        hentGrunnlagSkattepliktig()
            .map { (gjelderIdent, grunnlag) ->
                behandling.opprettGrunnlag(
                    Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                    grunnlag,
                    gjelderIdent,
                    innhentetTidspunkt(Grunnlagstype.INNHENTET_INNTEKT_SKATTEGRUNNLAG_PERIODE),
                    lesemodus,
                )
            },
    ).flatten()

fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    rolleIdent: String,
    innhentetTidspunkt: LocalDateTime,
    lesemodus: Boolean,
    erBearbeidet: Boolean = false,
    gjelder: String? = null,
) = Grunnlag(
    behandling = this,
    id = if (lesemodus) 1 else null,
    innhentet = innhentetTidspunkt,
    data = commonObjectmapper.writeValueAsString(grunnlag),
    type = type,
    erBearbeidet = erBearbeidet,
    gjelder = gjelder,
    aktiv = innhentetTidspunkt,
    rolle = roller.find { it.ident == rolleIdent }!!,
)

private fun VedtakDto.notatMedType(
    type: NotatGrunnlag.NotatType,
    medIVedtak: Boolean,
) = grunnlagListe
    .filtrerBasertPåEgenReferanse(Grunnlagstype.NOTAT)
    .map { it.innholdTilObjekt<NotatGrunnlag>() }
    .find { it.type == type && it.erMedIVedtaksdokumentet == medIVedtak }
    ?.innhold

private fun VedtakDto.avslagskode(): Resultatkode? =
    if (stønadsendringListe.all { it.periodeListe.size == 1 }) {
        val virkningstidspunkt = hentVirkningstidspunkt()
        if (virkningstidspunkt == null) {
            Resultatkode.fraKode(
                stønadsendringListe
                    .first()
                    .periodeListe
                    .first()
                    .resultatkode,
            )
        } else if (virkningstidspunkt.avslag != null) {
            virkningstidspunkt.avslag
        } else {
            null
        }
    } else {
        null
    }

private fun VedtakDto.hentVirkningstidspunkt(): VirkningstidspunktGrunnlag? =
    grunnlagListe
        .filtrerBasertPåEgenReferanse(Grunnlagstype.VIRKNINGSTIDSPUNKT)
        .firstOrNull()
        ?.innholdTilObjekt<VirkningstidspunktGrunnlag>()

private fun VedtakDto.hentSøknad(): SøknadGrunnlag =
    grunnlagListe
        .filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD)
        .first()
        .innholdTilObjekt<SøknadGrunnlag>()

private fun List<BaseGrunnlag>.tilHusstandsmedlem(
    gjelderReferanse: Grunnlagsreferanse,
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
): Husstandsmedlem {
    val gjelderGrunnlag =
        grunnlagsListe.hentPersonMedReferanse(gjelderReferanse) ?: manglerPersonGrunnlag(
            gjelderReferanse,
        )

    val gjelderPerson = gjelderGrunnlag.innholdTilObjekt<Person>()

    val gjelderRolle =
        behandling.roller
            .find { hentNyesteIdent(it.ident) == hentNyesteIdent(gjelderPerson.ident?.verdi) }
    val erBmBpBosstatus =
        gjelderRolle?.let { listOf(Rolletype.BIDRAGSPLIKTIG, Rolletype.BIDRAGSMOTTAKER).contains(it.rolletype) }
            ?: false
    val erOffentligKilde =
        grunnlagsListe
            .hentInnhentetHusstandsmedlem()
            .any { it.gjelderPersonId == gjelderGrunnlag.personIdent }
    val husstandsmedlemBO =
        Husstandsmedlem(
            ident = gjelderGrunnlag.personIdent,
            navn = gjelderPerson.navn,
            fødselsdato = gjelderPerson.fødselsdato,
            kilde = if (erOffentligKilde || erBmBpBosstatus) Kilde.OFFENTLIG else Kilde.MANUELL,
            behandling = behandling,
            rolle = gjelderRolle,
        )
    husstandsmedlemBO.perioder =
        this
            .map {
                val bosstatusPeriode = it.innholdTilObjekt<BostatusPeriode>()
                Bostatusperiode(
                    husstandsmedlem = husstandsmedlemBO,
                    datoFom = bosstatusPeriode.periode.fom.atDay(1),
                    datoTom =
                        bosstatusPeriode.periode.til
                            ?.atDay(1)
                            ?.minusDays(1),
                    bostatus = bosstatusPeriode.bostatus,
                    kilde = if (bosstatusPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
                )
            }.toMutableSet()
    return husstandsmedlemBO
}

private fun BaseGrunnlag.tilSivilstand(
    behandling: Behandling,
    id: Long? = null,
): Sivilstand {
    val sivilstandPeriode = innholdTilObjekt<SivilstandPeriode>()

    return Sivilstand(
        id = id,
        sivilstand = sivilstandPeriode.sivilstand,
        datoFom = sivilstandPeriode.periode.fom.atDay(1),
        datoTom =
            sivilstandPeriode.periode.til
                ?.atDay(1)
                ?.minusDays(1),
        behandling = behandling,
        kilde = if (sivilstandPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
    )
}

private fun BaseGrunnlag.tilInntekt(
    behandling: Behandling,
    grunnlagsListe: List<GrunnlagDto>,
    id: Long? = null,
): Inntekt {
    val inntektPeriode = innholdTilObjekt<InntektsrapporteringPeriode>()
    val gjelderBarn = grunnlagsListe.hentPersonMedReferanse(inntektPeriode.gjelderBarn)
    val gjelder =
        grunnlagsListe.hentPersonMedReferanse(gjelderReferanse) ?: manglerPersonGrunnlag(
            gjelderReferanse,
        )
    if (inntektsrapporteringSomKreverBarn.contains(inntektPeriode.inntektsrapportering) && gjelderBarn == null) {
        vedtakmappingFeilet(
            "Mangler barn for inntekt ${inntektPeriode.inntektsrapportering} med referanse $referanse i grunnlagslisten",
        )
    }
    val datoFom = if (inntektPeriode.valgt) inntektPeriode.periode.fom.atDay(1) else null
    val datoTom =
        if (inntektPeriode.valgt) {
            inntektPeriode.periode.til
                ?.atDay(1)
                ?.minusDays(1)
        } else {
            null
        }
    val opprinneligFom = inntektPeriode.opprinneligPeriode?.fom?.atDay(1)
    val opprinneligTom =
        inntektPeriode.opprinneligPeriode
            ?.til
            ?.atDay(1)
            ?.minusDays(1)
    val inntektBO =
        Inntekt(
            id = id,
            type = inntektPeriode.inntektsrapportering,
            belop = inntektPeriode.beløp,
            gjelderBarn = gjelderBarn?.personIdent,
            taMed = inntektPeriode.valgt,
            datoFom = datoFom,
            datoTom = datoTom,
            opprinneligFom =
                if (!inntektPeriode.manueltRegistrert) {
                    opprinneligFom
                        ?: datoFom
                } else {
                    null
                },
            opprinneligTom =
                if (!inntektPeriode.manueltRegistrert) {
                    opprinneligTom
                        ?: datoTom
                } else {
                    null
                },
            ident = gjelder.personIdent!!,
            kilde = if (inntektPeriode.manueltRegistrert) Kilde.MANUELL else Kilde.OFFENTLIG,
            behandling = behandling,
        )

    inntektBO.inntektsposter =
        inntektPeriode.inntekstpostListe
            .mapIndexed { i, it ->
                Inntektspost(
                    id = if (id != null) id + i else null,
                    kode = it.kode,
                    inntektstype = it.inntekstype,
                    beløp = it.beløp,
                    inntekt = inntektBO,
                )
            }.toMutableSet()

    return inntektBO
}

private fun GrunnlagDto.tilRolle(
    behandling: Behandling,
    id: Long? = null,
) = Rolle(
    behandling,
    id = id,
    rolletype =
        when (type) {
            Grunnlagstype.PERSON_SØKNADSBARN -> Rolletype.BARN
            Grunnlagstype.PERSON_BIDRAGSMOTTAKER -> Rolletype.BIDRAGSMOTTAKER
            Grunnlagstype.PERSON_REELL_MOTTAKER -> Rolletype.REELMOTTAKER
            Grunnlagstype.PERSON_BIDRAGSPLIKTIG -> Rolletype.BIDRAGSPLIKTIG
            else ->
                vedtakmappingFeilet(
                    "Ukjent rolletype $type",
                )
        },
    ident = personIdent,
    fødselsdato = personObjekt.fødselsdato,
)

private fun Inntekt.copy(
    type: Inntektsrapportering? = null,
    kilde: Kilde? = null,
): Inntekt {
    val nyInntekt =
        Inntekt(
            type = type ?: this.type,
            belop = belop,
            gjelderBarn = gjelderBarn,
            taMed = taMed,
            datoFom = datoFom,
            datoTom = datoTom,
            ident = ident,
            kilde = kilde ?: this.kilde,
            behandling = behandling,
            opprinneligFom = opprinneligFom,
            opprinneligTom = opprinneligTom,
        )
    nyInntekt.inntektsposter =
        inntektsposter
            .map {
                Inntektspost(
                    beløp = it.beløp,
                    inntektstype = it.inntektstype,
                    kode = it.kode,
                    inntekt = nyInntekt,
                )
            }.toMutableSet()
    return nyInntekt
}
