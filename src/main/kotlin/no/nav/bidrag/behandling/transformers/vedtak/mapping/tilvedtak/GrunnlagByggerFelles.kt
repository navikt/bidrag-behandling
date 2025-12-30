package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.service.NotatService.Companion.henteInntektsnotat
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.service.NotatService.Companion.henteSamværsnotat
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.grunnlag.hentGrunnlagsreferanserForInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.hentVersjonForInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.inntektManglerSøknadsbarn
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.inntekt.bestemDatoTomForOffentligInntekt
import no.nav.bidrag.behandling.transformers.kanSkriveVurderingAvSkolegangAlle
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.vedtak.inntektsrapporteringSomKreverSøknadsbarn
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.ResultatVedtak
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.EtterfølgendeManuelleVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManuellVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.ManueltOverstyrtGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SluttberegningGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilInnholdMedReferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettBehandlingsreferanseRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.felles.toYearMonth
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.YearMonth
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

val grunnlagsreferanse_delberegning_utgift = "delberegning_utgift"
val grunnlagsreferanse_utgiftsposter = "utgiftsposter"
val grunnlagsreferanse_utgift_direkte_betalt = "utgift_direkte_betalt"
val grunnlagsreferanse_utgift_maks_godkjent_beløp = "utgift_maks_godkjent_beløp"
val grunnlagsreferanse_løpende_bidrag = "løpende_bidrag_bidragspliktig"

fun opprettGrunnlagsreferanseVirkningstidspunkt(
    søknadsbarn: Rolle? = null,
    referanse: String? = null,
) = "virkningstidspunkt${søknadsbarn?.let { "_${it.tilGrunnlagsreferanse()}" } ?: referanse?.let {"_$referanse"} ?: ""}"

fun Collection<GrunnlagDto>.husstandsmedlemmer() = filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }

fun Behandling.byggGrunnlagGenerelt(søknadsbarn: List<Rolle> = this.søknadsbarn): Set<GrunnlagDto> {
    val grunnlagListe = (byggGrunnlagNotater(søknadsbarn) + byggGrunnlagSøknad(søknadsbarn)).toMutableSet()
    when (tilType()) {
        TypeBehandling.FORSKUDD -> {
            grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
        }

        TypeBehandling.SÆRBIDRAG -> {
            grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())
        }

        else -> {}
    }
    return grunnlagListe
}

fun BaseGrunnlag.tilOpprettRequestDto() =
    OpprettGrunnlagRequestDto(
        referanse = referanse,
        type = type,
        innhold = innhold,
        grunnlagsreferanseListe = grunnlagsreferanseListe,
        gjelderReferanse = gjelderReferanse,
        gjelderBarnReferanse = gjelderBarnReferanse,
    )

private fun opprettGrunnlagNotat(
    notatType: Notattype,
    medIVedtak: Boolean,
    innhold: String,
    gjelderReferanse: String? = null,
    gjelderBarnReferanse: String? = null,
    fraOmgjortVedtak: Boolean = false,
) = GrunnlagDto(
    referanse =
        "notat_${notatType}_${if (medIVedtak) "med_i_vedtaket" else "kun_i_notat"}" +
            (if (fraOmgjortVedtak) "_fra_opprinnelig_vedtak" else "") +
            "${gjelderReferanse?.let { "_$it" } ?: ""}${gjelderBarnReferanse?.let { "_$it" } ?: ""}",
    type = Grunnlagstype.NOTAT,
    gjelderReferanse = gjelderReferanse,
    gjelderBarnReferanse = gjelderBarnReferanse,
    innhold =
        POJONode(
            NotatGrunnlag(
                innhold = innhold,
                erMedIVedtaksdokumentet = medIVedtak,
                type = notatType,
                fraOmgjortVedtak = fraOmgjortVedtak,
            ),
        ),
)

fun Rolle.byggGrunnlagManueltOverstyrtGebyrRolle(søknadsid: Long): GrunnlagDto? {
    val gebyr = gebyrForSøknad(søknadsid)
    if (gebyr.manueltOverstyrtGebyr == null || !gebyr.manueltOverstyrtGebyr!!.overstyrGebyr) {
        return null
    }
    return GrunnlagDto(
        referanse = "${Grunnlagstype.MANUELT_OVERSTYRT_GEBYR}_${tilGrunnlagsreferanse()}_${gebyr.referanse}",
        type = Grunnlagstype.MANUELT_OVERSTYRT_GEBYR,
        gjelderReferanse = tilGrunnlagsreferanse(),
        innhold =
            POJONode(
                ManueltOverstyrtGebyr(
                    begrunnelse = gebyr.manueltOverstyrtGebyr!!.begrunnelse!!,
                    ilagtGebyr = gebyr.manueltOverstyrtGebyr!!.ilagtGebyr!!,
                ),
            ),
    )
}

fun Behandling.byggGrunnlagManueltOverstyrtGebyr() =
    roller
        .filter { it.harGebyrsøknad }
        .filter { it.hentEllerOpprettGebyr().overstyrGebyr }
        .flatMap { rolle ->
            rolle.gebyrSøknader.mapNotNull {
                rolle.byggGrunnlagManueltOverstyrtGebyrRolle(it.søknadsid)
            }
        }.toSet()

fun Behandling.byggGrunnlagSøknad(søknadsbarn: List<Rolle> = this.søknadsbarn) =
    if (erIForholdsmessigFordeling || erBisysVedtak) {
        søknadsbarn.flatMap {
            it.forholdsmessigFordeling!!.søknaderUnderBehandling.map { søknad ->
                GrunnlagDto(
                    referanse = "søknad_${it.tilGrunnlagsreferanse()}_${søknad.søknadsid}",
                    type = Grunnlagstype.SØKNAD,
                    gjelderReferanse = it.bidragsmottaker?.tilGrunnlagsreferanse(),
                    gjelderBarnReferanse = it.tilGrunnlagsreferanse(),
                    innhold =
                        POJONode(
                            SøknadGrunnlag(
                                klageMottattDato = omgjøringsdetaljer?.klageMottattdato,
                                mottattDato = søknad.mottattDato,
                                søktFraDato = søknad.søknadFomDato ?: søktFomDato,
                                søktAv = søknad.søktAvType,
                                begrensetRevurdering = søknad.behandlingstype?.erBegrensetRevurdering() == true,
                                innkrevingsgrunnlag = erInnkreving,
                                saksnummer = søknad.saksnummer ?: it.saksnummer,
                                egetTiltak =
                                    listOf(
                                        Behandlingstype.BEGRENSET_REVURDERING,
                                        Behandlingstype.EGET_TILTAK,
                                        Behandlingstype.PARAGRAF_35_C,
                                        Behandlingstype.PARAGRAF_35_C_BEGRENSET_SATS,
                                    ).contains(søknad.behandlingstype),
                                opprinneligVedtakstype = omgjøringsdetaljer?.opprinneligVedtakstype,
                                behandlingstype = søknad.behandlingstype,
                                behandlingstema = søknad.behandlingstema,
                                søknadsid = søknad.søknadsid,
                                privatAvtale = søknad.behandlingstype == Behandlingstype.PRIVAT_AVTALE,
                                paragraf35c =
                                    listOf(
                                        Behandlingstype.PARAGRAF_35_C_BEGRENSET_SATS,
                                        Behandlingstype.PARAGRAF_35_C,
                                    ).contains(søknad.behandlingstype),
                            ),
                        ),
                )
            }
        }
    } else {
        setOf(
            GrunnlagDto(
                referanse = "søknad",
                type = Grunnlagstype.SØKNAD,
                innhold =
                    POJONode(
                        SøknadGrunnlag(
                            klageMottattDato = omgjøringsdetaljer?.klageMottattdato,
                            mottattDato = mottattdato,
                            søktFraDato = søktFomDato,
                            søktAv = soknadFra,
                            begrensetRevurdering = søknadstype?.erBegrensetRevurdering() == true,
                            innkrevingsgrunnlag = erInnkreving,
                            saksnummer = saksnummer,
                            egetTiltak =
                                listOf(
                                    Behandlingstype.BEGRENSET_REVURDERING,
                                    Behandlingstype.EGET_TILTAK,
                                    Behandlingstype.PARAGRAF_35_C,
                                    Behandlingstype.PARAGRAF_35_C_BEGRENSET_SATS,
                                ).contains(søknadstype),
                            opprinneligVedtakstype = omgjøringsdetaljer?.opprinneligVedtakstype,
                            privatAvtale = søknadstype == Behandlingstype.PRIVAT_AVTALE,
                            paragraf35c =
                                listOf(
                                    Behandlingstype.PARAGRAF_35_C_BEGRENSET_SATS,
                                    Behandlingstype.PARAGRAF_35_C,
                                ).contains(søknadstype),
                        ),
                    ),
            ),
        )
    }

fun Behandling.byggGrunnlaggEtterfølgendeManuelleVedtak(grunnlagFraBeregning: List<GrunnlagDto>): Set<GrunnlagDto> =
    søknadsbarn
        .mapNotNull {
            val søknadsbarnGrunnlag = grunnlagFraBeregning.hentPerson(it.ident) ?: it.tilGrunnlagPerson()

            val grunnlag =
                grunnlag
                    .hentSisteGrunnlagSomGjelderBarn(
                        it.personident!!.verdi,
                        Grunnlagsdatatype.ETTERFØLGENDE_VEDTAK,
                    )
            val innhold = grunnlag?.konvertereData<List<VedtakForStønad>>() ?: return@mapNotNull null
            val gjelderReferanse =
                grunnlagFraBeregning.hentPerson(grunnlag.rolle.ident)?.referanse ?: grunnlag.rolle.tilGrunnlagsreferanse()
            GrunnlagDto(
                referanse = "${Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK}_${søknadsbarnGrunnlag.referanse}",
                type = Grunnlagstype.ETTERFØLGENDE_MANUELLE_VEDTAK,
                innhold =
                    POJONode(
                        EtterfølgendeManuelleVedtakGrunnlag(
                            vedtaksliste = innhold,
                        ),
                    ),
                gjelderReferanse = gjelderReferanse,
                gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
            )
        }.toSet()

fun Behandling.byggGrunnlagManuelleVedtak(grunnlagFraBeregning: List<GrunnlagDto>): Set<GrunnlagDto> =
    søknadsbarn
        .mapNotNull {
            val søknadsbarnGrunnlag = grunnlagFraBeregning.hentPerson(it.ident) ?: it.tilGrunnlagPerson()

            val grunnlag =
                grunnlag
                    .hentSisteGrunnlagSomGjelderBarn(
                        it.personident!!.verdi,
                        Grunnlagsdatatype.MANUELLE_VEDTAK,
                    )
            val innhold = grunnlag?.konvertereData<List<ManuellVedtakGrunnlag>>() ?: return@mapNotNull null
            val gjelderReferanse =
                grunnlagFraBeregning.hentPerson(grunnlag!!.rolle.ident)?.referanse ?: grunnlag.rolle.tilGrunnlagsreferanse()
            GrunnlagDto(
                referanse = "${Grunnlagstype.MANUELLE_VEDTAK}_${søknadsbarnGrunnlag.referanse}",
                type = Grunnlagstype.MANUELLE_VEDTAK,
                innhold =
                    POJONode(
                        innhold,
                    ),
                gjelderReferanse = gjelderReferanse,
                gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
            )
        }.toSet()

fun byggGrunnlagVirkningstidspunktResultatvedtak(
    resultatVedtak: ResultatVedtak,
    søknadsbarnreferanse: String,
): GrunnlagDto =
    GrunnlagDto(
        referanse = opprettGrunnlagsreferanseVirkningstidspunkt(null, søknadsbarnreferanse),
        type = Grunnlagstype.VIRKNINGSTIDSPUNKT,
        gjelderBarnReferanse = søknadsbarnreferanse,
        innhold =
            POJONode(
                VirkningstidspunktGrunnlag(
                    virkningstidspunkt =
                        resultatVedtak.resultat.beregnetBarnebidragPeriodeListe
                            .minOf { it.periode.fom }
                            .atDay(1),
                    opphørsdato =
                        resultatVedtak.resultat.beregnetBarnebidragPeriodeListe
                            .maxBy { it.periode.fom }
                            .periode.til
                            ?.atDay(1),
                    årsak = VirkningstidspunktÅrsakstype.AUTOMATISK_JUSTERING,
                    avslag = null,
                ),
            ),
    )

fun Behandling.byggGrunnlagVirkningsttidspunkt(grunnlagFraBeregning: List<GrunnlagDto> = emptyList()) =
    if (erBidrag()) {
        søknadsbarn
            .map { sb ->
                val søknadsbarnGrunnlag = grunnlagFraBeregning.hentPerson(sb.ident) ?: sb.tilGrunnlagPerson()
                val årsak = sb.årsak ?: årsak
                val avslag = sb.avslag ?: avslag
                GrunnlagDto(
                    referanse = opprettGrunnlagsreferanseVirkningstidspunkt(sb),
                    type = Grunnlagstype.VIRKNINGSTIDSPUNKT,
                    gjelderBarnReferanse = søknadsbarnGrunnlag.referanse,
                    innhold =
                        POJONode(
                            VirkningstidspunktGrunnlag(
                                virkningstidspunkt = sb.virkningstidspunkt ?: virkningstidspunkt!!,
                                opphørsdato = sb.opphørsdato,
                                årsak = årsak,
                                beregnTil = sb.beregnTil,
                                beregnTilDato = finnBeregnTilDatoBehandling(sb).toYearMonth(),
                                avslag = (årsak == null).ifTrue { avslag },
                            ),
                        ),
                )
            }.toSet()
    } else {
        setOf(
            GrunnlagDto(
                referanse = opprettGrunnlagsreferanseVirkningstidspunkt(),
                type = Grunnlagstype.VIRKNINGSTIDSPUNKT,
                innhold =
                    POJONode(
                        VirkningstidspunktGrunnlag(
                            virkningstidspunkt = virkningstidspunkt!!,
                            årsak = årsak,
                            avslag = (årsak == null).ifTrue { avslag },
                        ),
                    ),
            ),
        )
    }

fun Behandling.byggGrunnlagNotaterDirekteAvslag(): Set<GrunnlagDto> =
    byggGrunnlagBegrunnelseVirkningstidspunkt() +
        setOf(
            henteNotatinnhold(this, Notattype.UTGIFTER).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.UTGIFTER, false, it)
            },
        ).filterNotNull().toSet()

fun Behandling.byggGrunnlagBegrunnelseVirkningstidspunkt() =
    if (erBidrag()) {
        søknadsbarn
            .flatMap { rolle ->
                listOf(
                    henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT, rolle).takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(Notattype.VIRKNINGSTIDSPUNKT, false, it, gjelderBarnReferanse = rolle.tilGrunnlagsreferanse())
                    } ?: henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT).takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(Notattype.VIRKNINGSTIDSPUNKT, false, it)
                    },
                    henteNotatinnhold(
                        this,
                        Notattype.VIRKNINGSTIDSPUNKT,
                        rolle,
                        begrunnelseDelAvBehandlingen = false,
                    ).takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(
                            Notattype.VIRKNINGSTIDSPUNKT,
                            false,
                            it,
                            fraOmgjortVedtak = true,
                            gjelderBarnReferanse = rolle.tilGrunnlagsreferanse(),
                        )
                    } ?: henteNotatinnhold(
                        this,
                        Notattype.VIRKNINGSTIDSPUNKT,
                        begrunnelseDelAvBehandlingen = false,
                    ).takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(
                            Notattype.VIRKNINGSTIDSPUNKT,
                            false,
                            it,
                            fraOmgjortVedtak = true,
                        )
                    },
                )
            }.filterNotNull()
            .toSet()
    } else {
        setOf(
            henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.VIRKNINGSTIDSPUNKT, false, it, gjelderReferanse = bidragsmottaker?.tilGrunnlagsreferanse())
            },
            henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT, begrunnelseDelAvBehandlingen = false).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(
                    Notattype.VIRKNINGSTIDSPUNKT,
                    false,
                    it,
                    gjelderReferanse = bidragsmottaker?.tilGrunnlagsreferanse(),
                    fraOmgjortVedtak = true,
                )
            },
        ).filterNotNull().toSet()
    }

fun Behandling.byggGrunnlagPrivatAvtale() =
    roller
        .flatMap { rolle ->
            listOf(
                henteNotatinnhold(
                    this,
                    Notattype.PRIVAT_AVTALE,
                    rolle,
                ).takeIfNotNullOrEmpty { innhold ->
                    opprettGrunnlagNotat(Notattype.PRIVAT_AVTALE, false, innhold, gjelderBarnReferanse = rolle.tilGrunnlagsreferanse())
                },
                henteNotatinnhold(
                    this,
                    Notattype.PRIVAT_AVTALE,
                    rolle,
                    begrunnelseDelAvBehandlingen = false,
                ).takeIfNotNullOrEmpty { innhold ->
                    opprettGrunnlagNotat(
                        Notattype.PRIVAT_AVTALE,
                        false,
                        innhold,
                        gjelderBarnReferanse = rolle.tilGrunnlagsreferanse(),
                        fraOmgjortVedtak = true,
                    )
                },
            )
        }.filterNotNull()

fun Behandling.byggGrunnlagNotatVurderingAvSkolegang() =
    if (kanSkriveVurderingAvSkolegangAlle()) {
        roller
            .flatMap { rolle ->
                listOf(
                    henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG, rolle)
                        .takeIfNotNullOrEmpty { innhold ->
                            opprettGrunnlagNotat(
                                Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                                false,
                                innhold,
                                gjelderBarnReferanse = rolle.tilGrunnlagsreferanse(),
                            )
                        },
                    henteNotatinnhold(
                        this,
                        Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                        rolle,
                        begrunnelseDelAvBehandlingen = false,
                    ).takeIfNotNullOrEmpty { innhold ->
                        opprettGrunnlagNotat(
                            Notattype.VIRKNINGSTIDSPUNKT_VURDERING_AV_SKOLEGANG,
                            false,
                            innhold,
                            gjelderBarnReferanse = rolle.tilGrunnlagsreferanse(),
                            fraOmgjortVedtak = true,
                        )
                    },
                )
            }.filterNotNull()
    } else {
        emptyList()
    }

fun Behandling.byggGrunnlagNotaterInnkreving(): Set<GrunnlagDto> {
    val virkningstidspunktGrunnlag = byggGrunnlagBegrunnelseVirkningstidspunkt()
    val notatVurderingAvSkolegang = byggGrunnlagNotatVurderingAvSkolegang()
    val notatPrivatAvtale = byggGrunnlagPrivatAvtale()

    return (virkningstidspunktGrunnlag + notatVurderingAvSkolegang + notatPrivatAvtale).toSet()
}

fun Behandling.byggGrunnlagNotater(søknadsbarn: List<Rolle> = this.søknadsbarn): Set<GrunnlagDto> {
    val virkningstidspunktGrunnlag = byggGrunnlagBegrunnelseVirkningstidspunkt()
    val notatGrunnlag =
        setOf(
            henteNotatinnhold(this, Notattype.BOFORHOLD).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.BOFORHOLD, false, it)
            },
            henteNotatinnhold(this, Notattype.BOFORHOLD, begrunnelseDelAvBehandlingen = false).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.BOFORHOLD, false, it, fraOmgjortVedtak = true)
            },
            henteNotatinnhold(this, Notattype.UTGIFTER).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.UTGIFTER, false, it)
            },
            henteNotatinnhold(this, Notattype.UTGIFTER, begrunnelseDelAvBehandlingen = false).takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.UTGIFTER, false, it, fraOmgjortVedtak = true)
            },
        ).filterNotNull()

    val notatVurderingAvSkolegang = byggGrunnlagNotatVurderingAvSkolegang()

    val notatUnderhold =
        roller
            .flatMap { rolle ->
                listOf(
                    henteNotatinnhold(this, Notattype.UNDERHOLDSKOSTNAD, rolle).takeIfNotNullOrEmpty { innhold ->
                        opprettGrunnlagNotat(
                            Notattype.UNDERHOLDSKOSTNAD,
                            false,
                            innhold,
                            gjelderReferanse =
                                if (rolle.rolletype != Rolletype.BARN) {
                                    rolle.tilGrunnlagsreferanse()
                                } else {
                                    null
                                },
                            gjelderBarnReferanse =
                                if (rolle.rolletype == Rolletype.BARN) {
                                    rolle.tilGrunnlagsreferanse()
                                } else {
                                    null
                                },
                        )
                    },
                    henteNotatinnhold(this, Notattype.UNDERHOLDSKOSTNAD, rolle, begrunnelseDelAvBehandlingen = false)
                        .takeIfNotNullOrEmpty { innhold ->
                            opprettGrunnlagNotat(
                                Notattype.UNDERHOLDSKOSTNAD,
                                false,
                                innhold,
                                gjelderReferanse =
                                    if (rolle.rolletype != Rolletype.BARN) {
                                        rolle.tilGrunnlagsreferanse()
                                    } else {
                                        null
                                    },
                                gjelderBarnReferanse =
                                    if (rolle.rolletype == Rolletype.BARN) {
                                        rolle.tilGrunnlagsreferanse()
                                    } else {
                                        null
                                    },
                                fraOmgjortVedtak = true,
                            )
                        },
                )
            }.filterNotNull()

    val notatPrivatAvtale = byggGrunnlagPrivatAvtale()

    val notatSamvær =
        roller
            .flatMap { rolle ->
                listOf(
                    henteSamværsnotat(this, rolle)?.takeIfNotNullOrEmpty { innhold ->
                        opprettGrunnlagNotat(Notattype.SAMVÆR, false, innhold, gjelderBarnReferanse = rolle.tilGrunnlagsreferanse())
                    },
                    henteSamværsnotat(this, rolle, begrunnelseDelAvBehandlingen = false)?.takeIfNotNullOrEmpty { innhold ->
                        opprettGrunnlagNotat(
                            Notattype.SAMVÆR,
                            false,
                            innhold,
                            gjelderBarnReferanse = rolle.tilGrunnlagsreferanse(),
                            fraOmgjortVedtak = true,
                        )
                    },
                )
            }.filterNotNull()

    val notatGrunnlagInntekter =
        roller
            .flatMap { rolle ->
                listOf(
                    henteInntektsnotat(this, rolle.id!!)?.takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(
                            Notattype.INNTEKT,
                            false,
                            it,
                            gjelderReferanse = rolle.tilGrunnlagsreferanse(),
                        )
                    },
                    henteInntektsnotat(this, rolle.id!!, begrunnelseDelAvBehandlingen = false)?.takeIfNotNullOrEmpty {
                        opprettGrunnlagNotat(
                            Notattype.INNTEKT,
                            false,
                            it,
                            gjelderReferanse = rolle.tilGrunnlagsreferanse(),
                            fraOmgjortVedtak = true,
                        )
                    },
                )
            }.filterNotNull()

    return (
        virkningstidspunktGrunnlag + notatGrunnlag + notatGrunnlagInntekter + notatSamvær + notatUnderhold + notatVurderingAvSkolegang +
            notatPrivatAvtale
    ).toSet()
}

fun Behandling.tilSkyldner() =
    when (stonadstype) {
        Stønadstype.FORSKUDD -> {
            personIdentNav
        }

        else -> {
            bidragspliktig?.tilNyestePersonident()
                ?: rolleManglerIdent(Rolletype.BIDRAGSPLIKTIG, id!!)
        }
    }

fun Behandling.tilBehandlingreferanseListeUtenSøknad() =
    listOfNotNull(
        OpprettBehandlingsreferanseRequestDto(
            kilde = BehandlingsrefKilde.BEHANDLING_ID,
            referanse = id.toString(),
        ),
        omgjøringsdetaljer?.soknadRefId?.let {
            OpprettBehandlingsreferanseRequestDto(
                kilde = BehandlingsrefKilde.BISYS_KLAGE_REF_SØKNAD,
                referanse = it.toString(),
            )
        },
    )

fun Behandling.tilBehandlingreferanseListe(søknadsbarn: List<Rolle> = this.søknadsbarn) =
    tilBehandlingreferanseListeUtenSøknad() +
        if (erIForholdsmessigFordeling) {
            søknadsbarn
                .map { it.forholdsmessigFordeling!!.søknaderUnderBehandling }
                .flatMap {
                    it.filter { it.søknadsid != null }.map { søknad ->
                        OpprettBehandlingsreferanseRequestDto(
                            kilde = BehandlingsrefKilde.BISYS_SØKNAD,
                            referanse = søknad.søknadsid!!.toString(),
                        )
                    }
                }.toSet()
                .toList()
        } else {
            listOfNotNull(
                OpprettBehandlingsreferanseRequestDto(
                    kilde = BehandlingsrefKilde.BISYS_SØKNAD,
                    referanse = soknadsid.toString(),
                ),
            )
        }

internal fun Inntekt.tilGrunnlagreferanse(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto? = null,
): String {
    val datoFomReferanse = (opprinneligFom ?: datoFom).toCompactString()
    val datoTomReferanse = (opprinneligTom ?: datoTom)?.let { "_${it.toCompactString()}" } ?: ""
    return if (!gjelderBarn.isNullOrEmpty()) {
        val innektsposterType = inntektsposter.mapNotNull { it.inntektstype }.distinct().joinToString("", prefix = "_")
        val barnReferanse = søknadsbarn?.referanse ?: gjelderBarn.hashCode()
        "inntekt_${type}${innektsposterType}_${gjelder.referanse}_ba_${barnReferanse}_${datoFomReferanse}${datoTomReferanse}_$id"
    } else {
        "inntekt_${type}_${gjelder.referanse}_${datoFomReferanse}${datoTomReferanse}_$id"
    }
}

internal fun Person.valider(rolle: Rolletype? = null): Person {
    if ((ident == null || ident!!.verdi.isEmpty()) && navn.isNullOrEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Person med fødselsdato $fødselsdato og rolle $rolle mangler ident men har ikke navn. Ident eller Navn må være satt",
        )
    }
    return this
}

internal fun opprettGrunnlagForBostatusperioder(
    gjelderReferanse: String,
    relatertTilPartReferanse: String,
    bostatusperioder: Set<Bostatusperiode>,
): Set<GrunnlagDto> =
    bostatusperioder
        .map {
            GrunnlagDto(
                referanse = "bostatus_${gjelderReferanse}_${it.datoFom?.toCompactString()}",
                type = Grunnlagstype.BOSTATUS_PERIODE,
                gjelderReferanse = relatertTilPartReferanse,
                gjelderBarnReferanse = if (gjelderReferanse == relatertTilPartReferanse) null else gjelderReferanse,
                grunnlagsreferanseListe =
                    if (it.kilde == Kilde.OFFENTLIG) {
                        listOf(
                            opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                                relatertTilPartReferanse,
                                referanseRelatertTil = gjelderReferanse,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                innhold =
                    POJONode(
                        BostatusPeriode(
                            bostatus = it.bostatus,
                            manueltRegistrert = it.kilde == Kilde.MANUELL,
                            relatertTilPart = relatertTilPartReferanse,
                            periode =
                                ÅrMånedsperiode(
                                    it.datoFom!!,
                                    it.datoTom?.plusDays(1),
                                ),
                        ),
                    ),
            )
        }.toSet()

internal fun SluttberegningGebyr.tilResultatkode() = if (ilagtGebyr) Resultatkode.GEBYR_ILAGT else Resultatkode.GEBYR_FRITATT

fun List<BaseGrunnlag>.finnInntektSiste12Mnd(rolle: Rolle) =
    filter {
        it.type == Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE && it.gjelderReferanse == rolle.tilGrunnlagsreferanse()
    }.find { it.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }
        ?.tilInnholdMedReferanse<InntektsrapporteringPeriode>()

internal fun Inntekt.tilInntektsrapporteringPeriode(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto?,
    grunnlagListe: List<Grunnlag> = emptyList(),
    skalTasMed: Boolean? = null,
    periode: ÅrMånedsperiode? = null,
) = GrunnlagDto(
    type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
    referanse = tilGrunnlagreferanse(gjelder, søknadsbarn),
    // Liste med referanser fra bidrag-inntekt
    grunnlagsreferanseListe =
        grunnlagListe.toSet().hentGrunnlagsreferanserForInntekt(
            gjelder.personIdent!!,
            this,
        ),
    gjelderReferanse = gjelder.referanse,
    gjelderBarnReferanse = søknadsbarn?.referanse,
    innhold =
        POJONode(
            InntektsrapporteringPeriode(
                beløp = belop,
                versjon = (kilde == Kilde.OFFENTLIG).ifTrue { grunnlagListe.hentVersjonForInntekt(this) },
                periode =
                    if (periode != null) {
                        periode
                    } else if (kilde == Kilde.OFFENTLIG && eksplisitteYtelser.contains(type)) {
                        ÅrMånedsperiode(opprinneligFom!!, bestemDatoTomForOffentligInntekt()?.plusDays(1))
                    } else {
                        ÅrMånedsperiode(datoFomEllerOpprinneligFom!!, datoTom?.plusDays(1))
                    },
                opprinneligPeriode =
                    if (kilde == Kilde.OFFENTLIG) {
                        ÅrMånedsperiode(
                            opprinneligFom!!,
                            opprinneligTom?.plusDays(1),
                        )
                    } else {
                        null
                    },
                inntektsrapportering = type,
                manueltRegistrert = kilde == Kilde.MANUELL,
                valgt = skalTasMed ?: taMed,
                inntektspostListe =
                    inntektsposter.map {
                        InntektsrapporteringPeriode.Inntektspost(
                            beløp = it.beløp,
                            inntektstype = it.inntektstype,
                            kode = it.kode,
                        )
                    },
                gjelderBarn =
                    if (inntektsrapporteringSomKreverSøknadsbarn.contains(type)) {
                        søknadsbarn?.referanse
                            ?: inntektManglerSøknadsbarn(type)
                    } else {
                        null
                    },
            ),
        ),
)

fun opprettPeriodeOpphør(
    søknadsbarn: Rolle,
    periodeliste: List<OpprettPeriodeRequestDto>,
    type: TypeBehandling = TypeBehandling.BIDRAG,
): OpprettPeriodeRequestDto? =
    periodeliste.takeIfNotNullOrEmpty {
        søknadsbarn.opphørsdato?.let {
            val opphørsmåned = YearMonth.from(it)
            val sistePeriode = periodeliste.maxBy { it.periode.fom }
            if (sistePeriode.periode.til != opphørsmåned) {
                ugyldigForespørsel("Siste periode i beregningen $sistePeriode er ikke lik opphørsdato $opphørsmåned")
            }
            OpprettPeriodeRequestDto(
                periode = ÅrMånedsperiode(it, null),
                resultatkode = Resultatkode.OPPHØR.name,
                beløp = null,
                grunnlagReferanseListe =
                    listOf(
                        if (type == TypeBehandling.BIDRAG) {
                            opprettGrunnlagsreferanseVirkningstidspunkt(søknadsbarn)
                        } else {
                            opprettGrunnlagsreferanseVirkningstidspunkt()
                        },
                    ),
            )
        } ?: periodeliste.maxBy { it.periode.fom }.periode.til?.let {
            OpprettPeriodeRequestDto(
                periode = ÅrMånedsperiode(it, null),
                resultatkode = Resultatkode.OPPHØR.name,
                beløp = null,
                grunnlagReferanseListe =
                    listOf(
                        if (type == TypeBehandling.BIDRAG) {
                            opprettGrunnlagsreferanseVirkningstidspunkt(søknadsbarn)
                        } else {
                            opprettGrunnlagsreferanseVirkningstidspunkt()
                        },
                    ),
            )
        }
    }
