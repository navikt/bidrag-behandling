package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BeregnTil
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.fantIkkeRolleISak
import no.nav.bidrag.behandling.service.BarnebidragGrunnlagInnhenting
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.beregning.EvnevurderingBeregningResultat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.behandling.transformers.grunnlag.manglerRolleIGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.mapAinntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.hentBeløpshistorikk
import no.nav.bidrag.behandling.transformers.tilInntektberegningDto
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.beregn.barnebidrag.BeregnGebyrApi
import no.nav.bidrag.beregn.core.BeregnApi
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Beregningstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.belopshistorikk.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.BidragsberegningOrkestratorRequest
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.KlageOrkestratorGrunnlag
import no.nav.bidrag.transport.behandling.beregning.barnebidrag.KlageOrkestratorManuellAldersjustering
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.gebyrBeløp
import no.nav.bidrag.transport.behandling.felles.grunnlag.gebyrDelberegningSumInntekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.sluttberegningGebyr
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun Behandling.finnInnkrevesFraDato(søknadsbarnRolle: Rolle) =
    if (innkrevingstype == Innkrevingstype.UTEN_INNKREVING) {
//        val beløpshistorikk = hentSisteBeløpshistorikk(tilStønadsid(søknadsbarnRolle))
        val beløpshistorikk = hentBeløpshistorikk(søknadsbarnRolle).konvertereData<StønadDto>()
        beløpshistorikk?.periodeListe?.minOfOrNull { it.periode.fom }
    } else {
        null
        // (søknadsbarnRolle.opprinneligVirkningstidspunkt ?: søknadsbarnRolle.virkningstidspunkt)!!.toYearMonth()
    }

fun Behandling.finnBeregnTilDatoBehandling(
    opphørsdato: YearMonth? = null,
    søknadsbarnRolle: Rolle? = null,
) = if (tilType() == TypeBehandling.SÆRBIDRAG) {
    virkningstidspunkt!!.plusMonths(1).withDayOfMonth(1)
} else if (erKlageEllerOmgjøring && klagedetaljer?.opprinneligVedtakstidspunkt?.isNotEmpty() == true) {
    when {
        søknadsbarnRolle?.beregnTil == BeregnTil.INNEVÆRENDE_MÅNED ->
            finnBeregnTilDato(virkningstidspunkt!!, opphørsdato ?: globalOpphørsdatoYearMonth)

        else -> {
            val beregnTilDato =
                klagedetaljer
                    ?.opprinneligVedtakstidspunkt!!
                    .min()
                    .plusMonths(1)
                    .withDayOfMonth(1)
                    .toLocalDate()

            val virkningstidspunkt = søknadsbarnRolle?.virkningstidspunkt ?: this.virkningstidspunkt!!
            if (virkningstidspunkt >= beregnTilDato) {
                virkningstidspunkt.plusMonths(1).withDayOfMonth(1)
            } else {
                beregnTilDato
            }
        }
    }
} else {
    finnBeregnTilDato(virkningstidspunkt!!, opphørsdato ?: globalOpphørsdatoYearMonth)
}

fun finnBeregnTilDato(
    virkningstidspunkt: LocalDate,
    opphørsdato: YearMonth? = null,
): LocalDate =
    if (opphørsdato == null || opphørsdato.isAfter(YearMonth.now().plusMonths(1))) {
        maxOf(YearMonth.now().plusMonths(1).atDay(1), virkningstidspunkt.plusMonths(1).withDayOfMonth(1))
    } else {
        opphørsdato.atDay(1)
    }

@Component
class VedtakGrunnlagMapper(
    val mapper: BehandlingTilGrunnlagMappingV2,
    val validering: ValiderBeregning,
    private val beregningEvnevurderingService: BeregningEvnevurderingService,
    private val barnebidragGrunnlagInnhenting: BarnebidragGrunnlagInnhenting,
    private val personService: PersonService,
    private val beregnGebyrApi: BeregnGebyrApi,
) {
    fun Behandling.tilSærbidragAvslagskode() = validering.run { tilSærbidragAvslagskode() }

    fun Behandling.tilPersonobjekter(søknadsbarnRolle: Rolle? = null) = mapper.run { tilPersonobjekter(søknadsbarnRolle) }

    fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto =
        roller.firstOrNull { personService.hentNyesteIdent(it.fødselsnummer?.verdi) == personService.hentNyesteIdent(fnr) }
            ?: fantIkkeRolleISak(saksnummer.verdi, fnr)

    fun Behandling.byggGrunnlagForAvslagUgyldigUtgifter() =
        mapper.run {
            listOf(
                tilPersonobjekter() + byggGrunnlagUtgiftsposter() +
                    byggGrunnlagUtgiftDirekteBetalt() +
                    byggGrunnlagUtgiftMaksGodkjentBeløp(),
                byggGrunnlagGenereltAvslag(),
            )
        }

    private fun Behandling.gebyrGrunnlagslisteDefaultVerdi(rolle: Rolle) =
        if (avslag != null) {
            emptyList()
        } else {
            beregnetInntekterGrunnlagForRolle(rolle)
        }

    fun beregnGebyr(
        behandling: Behandling,
        rolle: Rolle,
        grunnlagsliste: List<GrunnlagDto> = behandling.gebyrGrunnlagslisteDefaultVerdi(rolle),
    ): BeregnGebyrResultat {
        val gebyrBeregning =
            if (behandling.avslag != null) {
                beregnGebyrApi.beregnGebyr(grunnlagsliste, rolle.tilGrunnlagsreferanse()) +
                    mapper.run {
                        val grunnlagSkatteGrunnlag = behandling.tilGrunnlagInntektSiste12Mnd(rolle)
                        if (grunnlagSkatteGrunnlag != null) {
                            listOf(grunnlagSkatteGrunnlag) +
                                behandling.grunnlag
                                    .toList()
                                    .mapAinntekt(behandling.tilPersonobjekter())
                                    .filter { it.gjelderReferanse == rolle.tilGrunnlagsreferanse() }
                        } else {
                            emptyList()
                        }
                    }
            } else {
                beregnGebyrApi.beregnGebyr(grunnlagsliste, rolle.tilGrunnlagsreferanse())
            }
        val delberegningSumInntekt = gebyrBeregning.gebyrDelberegningSumInntekt
        val inntektSiste12Mnd = gebyrBeregning.finnInntektSiste12Mnd(rolle)
        return BeregnGebyrResultat(
            skattepliktigInntekt =
                delberegningSumInntekt?.skattepliktigInntekt ?: inntektSiste12Mnd?.innhold?.beløp ?: BigDecimal.ZERO,
            maksBarnetillegg = delberegningSumInntekt?.barnetillegg,
            resultatkode = gebyrBeregning.sluttberegningGebyr!!.innhold.tilResultatkode(),
            beløpGebyrsats = gebyrBeregning.gebyrBeløp!!,
            grunnlagsreferanseListeEngangsbeløp =
                listOfNotNull(
                    gebyrBeregning.sluttberegningGebyr!!.referanse,
                    inntektSiste12Mnd?.referanse,
                ),
            ilagtGebyr = gebyrBeregning.sluttberegningGebyr!!.innhold.ilagtGebyr,
            grunnlagsliste = gebyrBeregning,
        )
    }

    fun Behandling.beregnetInntekterGrunnlagForRolle(rolle: Rolle) =
        BeregnApi()
            .beregnInntekt(tilInntektberegningDto(rolle))
            .inntektPerBarnListe
            .filter { it.inntektGjelderBarnIdent != null }
            .flatMap { beregningBarn ->
                beregningBarn.summertInntektListe.map {
                    GrunnlagDto(
                        referanse = "${Grunnlagstype.DELBEREGNING_SUM_INNTEKT}_${rolle.tilGrunnlagsreferanse()}",
                        type = Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                        innhold = POJONode(it),
                        gjelderReferanse = rolle.tilGrunnlagsreferanse(),
                        gjelderBarnReferanse = beregningBarn.inntektGjelderBarnIdent!!.verdi,
                    )
                }
            }

    fun byggGrunnlagForBeregningPrivatAvtale(
        behandling: Behandling,
        person: no.nav.bidrag.behandling.database.datamodell.Person,
    ): BeregnGrunnlag {
        mapper.run {
            behandling.run {
                val personobjekter = tilPersonobjekter()
                val privatavtaleGrunnlag = tilPrivatAvtaleGrunnlag(personobjekter)

                val personObjekt = personobjekter.hentPerson(person.ident)!!
                val beregnFraDato = virkningstidspunkt ?: vedtakmappingFeilet("Virkningstidspunkt må settes for beregning")
                val opphørsdato = person.opphørsdatoForRolle(behandling)
                val beregningTilDato = finnBeregnTilDatoBehandling(opphørsdato)
                return BeregnGrunnlag(
                    periode =
                        ÅrMånedsperiode(
                            beregnFraDato,
                            beregningTilDato,
                        ),
                    opphørsdato = opphørsdato,
                    stønadstype = stonadstype ?: Stønadstype.BIDRAG,
                    søknadsbarnReferanse = personObjekt.referanse,
                    grunnlagListe = (personobjekter + privatavtaleGrunnlag).toSet().toList(),
                )
            }
        }
    }

    fun byggGrunnlagForBeregning(
        behandling: Behandling,
        søknadsbarnRolle: Rolle,
        endeligBeregning: Boolean = true,
    ): BidragsberegningOrkestratorRequest {
        mapper.run {
            behandling.run {
                val personobjekter = tilPersonobjekter(søknadsbarnRolle)
                val søknadsbarn = søknadsbarnRolle.tilGrunnlagPerson()
                val bostatusBarn = tilGrunnlagBostatus(personobjekter)
                val inntekter = tilGrunnlagInntekt(personobjekter, søknadsbarn, false)
                val grunnlagsliste =
                    (personobjekter + bostatusBarn + inntekter + byggGrunnlagSøknad() + byggGrunnlagVirkningsttidspunkt())
                        .toMutableSet()

                when (tilType()) {
                    TypeBehandling.FORSKUDD ->
                        grunnlagsliste.addAll(
                            tilGrunnlagSivilstand(
                                personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                            ),
                        )

                    TypeBehandling.SÆRBIDRAG -> {
                        grunnlagsliste.add(tilGrunnlagUtgift())
                        val grunnlagLøpendeBidrag =
                            beregningEvnevurderingService
                                .hentLøpendeBidragForBehandling(behandling)
                                .tilGrunnlagDto(grunnlagsliste)
                        grunnlagsliste.addAll(grunnlagLøpendeBidrag)
                    }

                    TypeBehandling.BIDRAG, TypeBehandling.BIDRAG_18_ÅR -> {
                        grunnlagsliste.addAll(tilPrivatAvtaleGrunnlag(grunnlagsliste))
                        grunnlagsliste.addAll(tilGrunnlagUnderholdskostnad(grunnlagsliste))
                        grunnlagsliste.addAll(tilGrunnlagSamvær(søknadsbarn))
                        grunnlagsliste.addAll(opprettMidlertidligPersonobjekterBMsbarn(grunnlagsliste.filter { it.erPerson() }.toSet()))

                        grunnlagsliste.addAll(barnebidragGrunnlagInnhenting.byggGrunnlagBeløpshistorikk(this, søknadsbarnRolle))
                    }
                }
                val beregnFraDato =
                    søknadsbarnRolle.virkningstidspunkt ?: behandling.virkningstidspunkt
                        ?: vedtakmappingFeilet("Virkningstidspunkt må settes for beregning")
                val beregningTilDato = finnBeregnTilDatoBehandling(null, søknadsbarnRolle)
                val grunnlagBeregning =
                    BeregnGrunnlag(
                        periode =
                            ÅrMånedsperiode(
                                beregnFraDato,
                                beregningTilDato,
                            ),
                        stønadstype = stonadstype ?: Stønadstype.BIDRAG,
                        opphørsdato = søknadsbarnRolle.opphørsdatoYearMonth,
                        søknadsbarnReferanse = søknadsbarn.referanse,
                        grunnlagListe = grunnlagsliste.toSet().toList(),
                    )
                val klageBeregning =
                    if (behandling.erKlageEllerOmgjøring && behandling.erBidrag()) {
                        KlageOrkestratorGrunnlag(
                            stønad = behandling.tilStønadsid(søknadsbarnRolle),
                            påklagetVedtakId = behandling.klagedetaljer?.påklagetVedtak!!,
                            innkrevingstype = behandling.innkrevingstype ?: Innkrevingstype.MED_INNKREVING,
                            gjelderParagraf35c =
                                listOf(
                                    BisysSøknadstype.PARAGRAF_35_C,
                                    BisysSøknadstype.PARAGRAF_35_C_BEGRENSET_SATS,
                                ).contains(behandling.søknadstype),
                            manuellAldersjustering =
                                søknadsbarnRolle.grunnlagFraVedtakListe
                                    .filter { it.aldersjusteringForÅr != null && it.vedtak != null }
                                    .map {
                                        KlageOrkestratorManuellAldersjustering(
                                            it.aldersjusteringForÅr!!,
                                            it.vedtak!!,
                                        )
                                    },
                        )
                    } else {
                        null
                    }
                return BidragsberegningOrkestratorRequest(
                    beregnGrunnlag = grunnlagBeregning,
                    klageOrkestratorGrunnlag = klageBeregning,
                    beregningstype =
                        when {
                            behandling.erKlageEllerOmgjøring -> if (endeligBeregning) Beregningstype.KLAGE_ENDELIG else Beregningstype.KLAGE
                            else -> Beregningstype.BIDRAG
                        },
                )
            }
        }
    }

    fun Behandling.byggGrunnlagForVedtak(personobjekterFraBeregning: MutableSet<GrunnlagDto> = mutableSetOf()): Set<GrunnlagDto> {
        mapper.run {
            val personobjekter = (tilPersonobjekter() + personobjekterFraBeregning).toSet()
            val bostatus = tilGrunnlagBostatus(personobjekter)
            val personobjekterMedHusstandsmedlemmer =
                (personobjekter + bostatus.husstandsmedlemmer()).toMutableSet()
            val innhentetGrunnlagListe = byggInnhentetGrunnlag(personobjekterMedHusstandsmedlemmer)
            val inntekter = tilGrunnlagInntekt(personobjekter)

            val grunnlagListe = (personobjekter + bostatus + inntekter + innhentetGrunnlagListe).toMutableSet()
            when (tilType()) {
                TypeBehandling.FORSKUDD ->
                    grunnlagListe.addAll(
                        tilGrunnlagSivilstand(
                            personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                        ),
                    )

                TypeBehandling.SÆRBIDRAG ->
                    grunnlagListe.addAll(
                        byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt() + byggGrunnlagUtgiftMaksGodkjentBeløp(),
                    )

                TypeBehandling.BIDRAG -> {
                    grunnlagListe.addAll(tilGrunnlagBarnetilsyn(true))
                }

                else -> {}
            }
            return grunnlagListe.toSet()
        }
    }

    fun Behandling.byggGrunnlagGenereltAvslag(): Set<GrunnlagDto> {
        val grunnlagListe = (byggGrunnlagNotaterDirekteAvslag() + byggGrunnlagSøknad()).toMutableSet()
        when (tilType()) {
            TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
            TypeBehandling.SÆRBIDRAG -> {
                grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())
                if (validering.run { tilSærbidragAvslagskode() } == Resultatkode.ALLE_UTGIFTER_ER_FORELDET) {
                    grunnlagListe.addAll(byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt())
                }
            }

            else -> {}
        }
        return grunnlagListe
    }

    fun EvnevurderingBeregningResultat.tilGrunnlagDto(personGrunnlagListe: MutableSet<GrunnlagDto>): List<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun LøpendeBidragssak.tilPersonGrunnlag(): GrunnlagDto {
            val fødselsdato = personService.hentPersonFødselsdato(kravhaver.verdi) ?: fantIkkeFødselsdatoTilSøknadsbarn(-1)
            val nyesteIdent = (personService.hentNyesteIdent(kravhaver.verdi) ?: kravhaver)

            return GrunnlagDto(
                referanse =
                    Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG.tilPersonreferanse(
                        fødselsdato.toCompactString(),
                        (kravhaver.verdi + 1).hashCode(),
                    ),
                type = Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG,
                innhold =
                    POJONode(
                        Person(
                            ident = nyesteIdent,
                            fødselsdato = fødselsdato,
                        ).valider(),
                    ),
            )
        }

        fun LøpendeBidragssak.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        val grunnlag =
            GrunnlagDto(
                referanse = grunnlagsreferanse_løpende_bidrag,
                gjelderReferanse = personGrunnlagListe.bidragspliktig!!.referanse,
                type = Grunnlagstype.LØPENDE_BIDRAG,
                innhold =
                    POJONode(
                        LøpendeBidragGrunnlag(
                            løpendeBidragListe =
                                løpendeBidragsaker.map { løpendeStønad ->
                                    val beregning = beregnetBeløpListe.beregningListe.find { it.personidentBarn == løpendeStønad.kravhaver }
                                    val personObjekt =
                                        personGrunnlagListe.hentPerson(løpendeStønad.kravhaver.verdi)
                                            ?: løpendeStønad.opprettPersonGrunnlag()
                                    LøpendeBidrag(
                                        faktiskBeløp = beregning?.faktiskBeløp ?: BigDecimal.ZERO,
                                        samværsklasse = beregning?.samværsklasse ?: Samværsklasse.SAMVÆRSKLASSE_0,
                                        beregnetBeløp = beregning?.beregnetBeløp ?: BigDecimal.ZERO,
                                        løpendeBeløp = løpendeStønad.løpendeBeløp,
                                        type = løpendeStønad.type,
                                        gjelderBarn = personObjekt.referanse,
                                        saksnummer = Saksnummer(løpendeStønad.sak.verdi),
                                        valutakode = løpendeStønad.valutakode,
                                    )
                                },
                        ),
                    ),
            )
        return grunnlagslistePersoner + mutableListOf(grunnlag)
    }
}
