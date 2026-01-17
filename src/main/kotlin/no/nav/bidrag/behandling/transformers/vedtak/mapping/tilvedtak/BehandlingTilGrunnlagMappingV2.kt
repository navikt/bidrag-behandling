package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.behandling.tilRolle
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.grunnlag.tilBeregnetInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetAndreBarnTilBidragsmottaker
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetArbeidsforhold
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagUnderholdskostnad
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetSivilstand
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.grunnlagsreferanseSimulert
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.opprettPersonBarnBPBMReferanse
import no.nav.bidrag.behandling.transformers.vedtak.tilPersonGrunnlag
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.FaktiskUtgiftPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtaleGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.PrivatAvtalePeriodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPersonMedReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.plus

@Component
class BehandlingTilGrunnlagMappingV2(
    val personService: PersonService,
    private val beregnSamværsklasseApi: BeregnSamværsklasseApi,
) {
    fun Behandling.tilPersonobjekter(
        søknadsbarnRolle: Rolle? = null,
        inkluderAlleSøknadsbarn: Boolean = false,
    ): MutableSet<GrunnlagDto> {
        val søknadsbarnListe =
            if (søknadsbarnRolle != null && !inkluderAlleSøknadsbarn) {
                listOf(søknadsbarnRolle.tilGrunnlagPerson())
            } else {
                søknadsbarn.map { it.tilGrunnlagPerson() }
            }

        val privatavtaleBarnSimulert = privatAvtale.filter { it.rolle == null }.map { it.tilPersonGrunnlag() }
        val bidragsmottakere = alleBidragsmottakere.map { it.tilGrunnlagPerson() }
        return (
            bidragsmottakere +
                listOf(
                    bidragspliktig?.tilGrunnlagPerson(),
                ) + søknadsbarnListe + privatavtaleBarnSimulert
        ).filterNotNull().toMutableSet()
    }

    fun Behandling.tilGrunnlagSivilstand(gjelder: BaseGrunnlag): Set<GrunnlagDto> =
        if (engangsbeloptype == Engangsbeløptype.SÆRBIDRAG) {
            emptySet()
        } else {
            sivilstand
                .map {
                    GrunnlagDto(
                        referanse = "sivilstand_${gjelder.referanse}_${it.datoFom.toCompactString()}",
                        type = Grunnlagstype.SIVILSTAND_PERIODE,
                        gjelderReferanse = gjelder.referanse,
                        grunnlagsreferanseListe =
                            if (it.kilde == Kilde.OFFENTLIG) {
                                listOf(
                                    opprettInnhentetSivilstandGrunnlagsreferanse(gjelder.referanse),
                                )
                            } else {
                                emptyList()
                            },
                        innhold =
                            POJONode(
                                SivilstandPeriode(
                                    manueltRegistrert = it.kilde == Kilde.MANUELL,
                                    sivilstand = it.sivilstand,
                                    periode = ÅrMånedsperiode(it.datoFom!!, it.datoTom?.plusDays(1)),
                                ),
                            ),
                    )
                }.toSet()
        }

    fun Behandling.byggInnhentetGrunnlag(personobjekter: MutableSet<GrunnlagDto>): Set<GrunnlagDto> {
        val sortertGrunnlagsListe = grunnlag.hentSisteAktiv()
        val sortertGrunnlagsListeBearbeidet = sortertGrunnlagsListe.filter { it.erBearbeidet }
        val sortertGrunnlagsListeIkkeBearbeidet = sortertGrunnlagsListe.filter { !it.erBearbeidet }
        val innhentetArbeidsforhold = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetArbeidsforhold(personobjekter)
        val innhentetSivilstand = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetSivilstand(personobjekter)
        val innhentetHusstandsmedlemmer =
            sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetHusstandsmedlemmer(personobjekter, this)
        val innhentetAndreBarnTilBM = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetAndreBarnTilBidragsmottaker(personobjekter)
        val beregnetInntekt = sortertGrunnlagsListeBearbeidet.tilBeregnetInntekt(personobjekter)
        val innhentetInntekter = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetGrunnlagInntekt(personobjekter)
        val innhentetUnderholdskostnad = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetGrunnlagUnderholdskostnad(personobjekter)

        return innhentetInntekter + innhentetArbeidsforhold + innhentetHusstandsmedlemmer +
            innhentetSivilstand + beregnetInntekt +
            innhentetUnderholdskostnad + innhentetAndreBarnTilBM
    }

    fun Rolle.tilGrunnlagPerson(): GrunnlagDto {
        val grunnlagstype = rolletype.tilGrunnlagstype()
        return GrunnlagDto(
            referanse = tilGrunnlagsreferanse(),
            type = grunnlagstype,
            gjelderReferanse = tilGrunnlagsreferanse(),
            innhold =
                POJONode(
                    Person(
                        ident = ident.takeIf { !it.isNullOrEmpty() }?.let { personService.hentNyesteIdent(it) ?: Personident(it) },
                        navn = if (ident.isNullOrEmpty()) navn ?: personService.hentPersonVisningsnavn(ident) else null,
                        bidragsmottaker =
                            if (grunnlagstype == Grunnlagstype.PERSON_SØKNADSBARN) {
                                bidragsmottaker?.tilGrunnlagsreferanse()
                            } else {
                                null
                            },
                        delAvOpprinneligBehandling =
                            if (forholdsmessigFordeling != null) {
                                !forholdsmessigFordeling!!.erRevurdering
                            } else {
                                true
                            },
                        fødselsdato =
                            finnFødselsdato(
                                ident,
                                fødselsdato,
                            ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                                ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id ?: -1),
                    ).valider(rolletype),
                ),
        )
    }

    fun Behandling.tilGrunnlagBostatus(personobjekter: Set<GrunnlagDto> = tilPersonobjekter()): Set<GrunnlagDto> {
        val personobjekterHusstandsmedlem = mutableSetOf<GrunnlagDto>()

        fun Husstandsmedlem.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilGrunnlagPerson()
            personobjekterHusstandsmedlem.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        val grunnlagBosstatus =
            husstandsmedlem
                .flatMap {
                    val gjelder =
                        if (it.rolle != null) {
                            personobjekter.hentPersonMedReferanse(it.rolle!!.tilGrunnlagsreferanse()) ?: it.opprettPersonGrunnlag()
                        } else {
                            personobjekter.hentPersonNyesteIdent(it.ident) ?: it.opprettPersonGrunnlag()
                        }
                    val part =
                        (if (stonadstype == Stønadstype.FORSKUDD) personobjekter.bidragsmottaker else personobjekter.bidragspliktig)
                    opprettGrunnlagForBostatusperioder(
                        gjelder.referanse,
                        part!!.referanse,
                        it.perioder,
                    )
                }.toSet()

        return grunnlagBosstatus + personobjekterHusstandsmedlem
    }

    fun Behandling.tilPrivatAvtaleGrunnlag(
        personobjekter: Set<GrunnlagDto>,
        gjelderBarnIdent: String? = null,
    ): Set<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun PrivatAvtale.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        return if (gjelderBarnIdent == null) {
            privatAvtale
                .filter { it.rolle == null }
                .flatMap { pa ->
                    val gjelderBarn =
                        personobjekter.hentPerson(pa.personIdent) ?: pa.opprettPersonGrunnlag()
                    val gjelderBarnReferanse = gjelderBarn.referanse
                    pa.mapTilGrunnlag(gjelderBarnReferanse, personobjekter.bidragspliktig!!.referanse, eldsteVirkningstidspunkt).toSet()
                }.toSet()
        } else {
            privatAvtale
                .find { it.perioderInnkreving.isNotEmpty() && it.personIdent == gjelderBarnIdent }
                ?.let { pa ->
                    val privatAvtaleRolle = pa.rolle
                    val gjelderBarn =
                        privatAvtaleRolle?.tilGrunnlagPerson()?.also {
                            grunnlagslistePersoner.add(it)
                        } ?: personobjekter.hentPerson(pa.personIdent) ?: pa.opprettPersonGrunnlag()
                    val gjelderBarnReferanse = gjelderBarn.referanse
                    pa.mapTilGrunnlag(gjelderBarnReferanse, personobjekter.bidragspliktig!!.referanse, eldsteVirkningstidspunkt).toSet()
                } ?: emptySet()
        }
    }

    fun Behandling.tilGrunnlagUnderholdskostnad(personobjekter: Set<GrunnlagDto> = emptySet()) =
        listOf(
            tilGrunnlagBarnetilsyn(),
            tilGrunnlagTilleggsstønad(),
            tilGrunnlagFaktiskeTilsynsutgifter(personobjekter),
        ).flatten()

    fun Behandling.tilGrunnlagInntektSiste12Mnd(rolle: Rolle) =
        tilGrunnlagInntekt()
            .filter { it.gjelderReferanse == rolle.tilGrunnlagsreferanse() }
            .find {
                it.innholdTilObjekt<InntektsrapporteringPeriode>().inntektsrapportering == Inntektsrapportering.AINNTEKT_BEREGNET_12MND
            }

    fun Behandling.tilGrunnlagInntektSimulering(personobjekter: Set<GrunnlagDto> = tilPersonobjekter()): Set<GrunnlagDto> {
        fun opprettManuellInntekt(gjelderReferanse: String) =
            GrunnlagDto(
                type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
                referanse = "inntekt_${gjelderReferanse}_${Inntektsrapportering.LØNN_MANUELT_BEREGNET}_$grunnlagsreferanseSimulert",
                grunnlagsreferanseListe = emptyList(),
                gjelderReferanse = gjelderReferanse,
                gjelderBarnReferanse = null,
                innhold =
                    POJONode(
                        InntektsrapporteringPeriode(
                            beløp = BigDecimal.ZERO,
                            versjon = null,
                            periode = ÅrMånedsperiode(eldsteVirkningstidspunkt, null),
                            opprinneligPeriode = null,
                            inntektsrapportering = Inntektsrapportering.LØNN_MANUELT_BEREGNET,
                            manueltRegistrert = true,
                            valgt = true,
                            inntektspostListe = emptyList(),
                            gjelderBarn = null,
                        ),
                    ),
            )

        val inntekterBPIkkeYtelse = inntekter.filter { it.ident == bidragspliktig?.ident && !eksplisitteYtelser.contains(it.type) }

        val grunnlagBp =
            if (inntekterBPIkkeYtelse.none { it.taMed }) {
                val gjelder = personobjekter.hentPersonNyesteIdent(bidragspliktig!!.ident)!!
                val ainntekt12Mnd = inntekterBPIkkeYtelse.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }
                if (ainntekt12Mnd != null) {
                    ainntekt12Mnd
                        .tilInntektsrapporteringPeriode(
                            gjelder,
                            null,
                            grunnlagListe,
                            skalTasMed = true,
                            periode = ÅrMånedsperiode(eldsteVirkningstidspunkt, null),
                        ).let {
                            it.copy(
                                referanse = "${it.referanse}_$grunnlagsreferanseSimulert",
                            )
                        }
                } else {
                    opprettManuellInntekt(gjelder.referanse)
                }
            } else {
                null
            }

        val inntekterBMIkkeYtelse = inntekter.filter { it.ident == bidragsmottaker?.ident && !eksplisitteYtelser.contains(it.type) }

        val grunnlagBm =
            if (inntekterBMIkkeYtelse.none { it.taMed }) {
                val gjelder = personobjekter.hentPersonNyesteIdent(bidragsmottaker!!.ident)!!
                val ainntekt12Mnd = inntekterBMIkkeYtelse.find { it.type == Inntektsrapportering.AINNTEKT_BEREGNET_12MND }
                if (ainntekt12Mnd != null) {
                    ainntekt12Mnd
                        .tilInntektsrapporteringPeriode(
                            gjelder,
                            null,
                            grunnlagListe,
                        ).let {
                            it.copy(
                                referanse = "${it.referanse}_$grunnlagsreferanseSimulert",
                            )
                        }
                } else {
                    opprettManuellInntekt(gjelder.referanse)
                }
            } else {
                null
            }
        return setOfNotNull(grunnlagBm, grunnlagBp)
    }

    fun Behandling.tilGrunnlagInntekt(
        personobjekter: Set<GrunnlagDto> = tilPersonobjekter(),
        søknadsbarn: GrunnlagDto? = null,
        inkluderAlle: Boolean = true,
    ): Set<GrunnlagDto> {
        val alleSøknadsbarnIdenter = this.søknadsbarn.mapNotNull { it.ident }
        return inntekter
            .asSequence()
            .filter { personobjekter.hentPersonNyesteIdent(it.ident) != null && (inkluderAlle || it.taMed) }
            .groupBy { it.ident }
            .flatMap { (ident, innhold) ->
                val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
                innhold
                    .filter {
                        if (søknadsbarn == null) {
                            it.gjelderBarn.isNullOrEmpty() ||
                                // Ikke ta med inntekter som ikke gjelder noen av søknadsbarna. Kan feks skje hvis en søknadsbarn er fjernet fra behandling
                                alleSøknadsbarnIdenter.contains(it.gjelderBarn)
                        } else {
                            val inntektTilhørerBarn =
                                if (gjelder.type == Grunnlagstype.PERSON_BIDRAGSMOTTAKER) {
                                    gjelder.referanse == søknadsbarn.innholdTilObjekt<Person>().bidragsmottaker
                                } else {
                                    true
                                }
                            inntektTilhørerBarn && (it.gjelderBarn == søknadsbarn.personIdent || it.gjelderBarn.isNullOrEmpty())
                        }
                    }.groupBy { it.gjelderBarn }
                    .map { (gjelderBarn, innhold) ->
                        val søknadsbarnGrunnlag = personobjekter.hentPersonNyesteIdent(gjelderBarn)
                        innhold.map {
                            it.tilInntektsrapporteringPeriode(
                                gjelder,
                                søknadsbarnGrunnlag,
                                grunnlagListe,
                            )
                        }
                    }.flatten()
            }.toSet()
    }

    fun Husstandsmedlem.tilGrunnlagPerson(): GrunnlagDto {
        val rolle = behandling.roller.find { it.ident == ident }
        val grunnlagstype = rolle?.rolletype?.tilGrunnlagstype() ?: Grunnlagstype.PERSON_HUSSTANDSMEDLEM
        val referanse =
            grunnlagstype.tilPersonreferanse(
                rolle?.fødselsdato?.toCompactString() ?: fødselsdato.toCompactString(),
                (rolle?.id ?: id!!).toInt(),
            )
        return GrunnlagDto(
            referanse = referanse,
            type = grunnlagstype,
            gjelderReferanse = referanse,
            innhold =
                POJONode(
                    Person(
                        ident = if (!ident.isNullOrEmpty()) Personident(ident!!) else null,
                        navn = if (ident.isNullOrEmpty()) navn ?: personService.hentPersonVisningsnavn(ident) else null,
                        bidragsmottaker =
                            if (grunnlagstype == Grunnlagstype.PERSON_SØKNADSBARN) {
                                rolle?.bidragsmottaker?.tilGrunnlagsreferanse()
                            } else {
                                null
                            },
                        delAvOpprinneligBehandling =
                            if (rolle?.forholdsmessigFordeling != null) {
                                rolle?.forholdsmessigFordeling!!.delAvOpprinneligBehandling
                            } else {
                                true
                            },
                        fødselsdato =
                            finnFødselsdato(
                                ident,
                                fødselsdato,
                            ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                                ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id ?: -1),
                    ).valider(),
                ),
        )
    }

    fun Behandling.tilGrunnlagSamværSimulering(): List<GrunnlagDto> =
        søknadsbarn.mapNotNull { barn ->
            val samværBarn = samvær.find { it.rolle.ident == barn.ident }
            if (samværBarn == null || samværBarn.perioder.isEmpty()) {
                GrunnlagDto(
                    referanse = "samvær_${Grunnlagstype.SAMVÆRSPERIODE}_${barn.tilGrunnlagsreferanse()}_$grunnlagsreferanseSimulert",
                    type = Grunnlagstype.SAMVÆRSPERIODE,
                    gjelderReferanse = bidragspliktig!!.tilGrunnlagsreferanse(),
                    grunnlagsreferanseListe = emptyList(),
                    gjelderBarnReferanse = barn.tilGrunnlagsreferanse(),
                    innhold =
                        POJONode(
                            SamværsperiodeGrunnlag(
                                periode = ÅrMånedsperiode(barn.finnBeregnFra(), null),
                                samværsklasse = Samværsklasse.SAMVÆRSKLASSE_0,
                            ),
                        ),
                )
            } else {
                null
            }
        }

    fun Behandling.tilGrunnlagSamvær(søknadsbarn: BaseGrunnlag? = null): List<GrunnlagDto> {
        val søknadsbarnIdent = søknadsbarn?.personIdent
        return samvær
            .filter { søknadsbarn == null || it.rolle.ident == søknadsbarnIdent }
            .flatMap { samvær ->
                val bpGrunnlagsreferanse = samvær.behandling.bidragspliktig!!.tilGrunnlagsreferanse()
                val barnGrunnlagsreferanse = samvær.rolle.tilGrunnlagPerson().referanse
                samvær.perioder.flatMap {
                    val grunnlagBeregning =
                        it.beregning?.let { beregnSamværsklasseApi.beregnSamværsklasse(it, bpGrunnlagsreferanse, barnGrunnlagsreferanse) }
                            ?: emptyList()
                    val grunnlagPeriode =
                        GrunnlagDto(
                            referanse = it.tilGrunnlagsreferanseSamværsperiode(),
                            type = Grunnlagstype.SAMVÆRSPERIODE,
                            gjelderReferanse = bpGrunnlagsreferanse,
                            grunnlagsreferanseListe =
                                grunnlagBeregning
                                    .filtrerBasertPåEgenReferanse(Grunnlagstype.DELBEREGNING_SAMVÆRSKLASSE)
                                    .map { it.referanse },
                            gjelderBarnReferanse = barnGrunnlagsreferanse,
                            innhold =
                                POJONode(
                                    SamværsperiodeGrunnlag(
                                        periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                        samværsklasse = it.samværsklasse,
                                    ),
                                ),
                        )
                    grunnlagBeregning + grunnlagPeriode
                }
            }
    }

    fun Behandling.tilGrunnlagFaktiskeTilsynsutgifter(personobjekter: Set<GrunnlagDto> = emptySet()): List<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun Underholdskostnad.tilPersonGrunnlag(bidragsmottaker: Rolle): GrunnlagDto {
            val referanse =
                opprettPersonBarnBPBMReferanse(
                    type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
                    personFødselsdato,
                    personIdent,
                    personNavn,
                )
            return GrunnlagDto(
                referanse = referanse,
                gjelderReferanse = bidragsmottaker.tilGrunnlagsreferanse(),
                grunnlagsreferanseListe =
                    if (kilde == Kilde.OFFENTLIG) {
                        listOf(
                            opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse(
                                bidragsmottaker.tilGrunnlagsreferanse(),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
                innhold =
                    POJONode(
                        Person(
                            ident = personIdent?.let { Personident(it) },
                            navn = if (personIdent.isNullOrEmpty()) personNavn else null,
                            fødselsdato = personFødselsdato,
                            bidragsmottaker = bidragsmottaker.tilGrunnlagsreferanse(),
                        ).valider(),
                    ),
            )
        }

        fun Underholdskostnad.opprettPersonGrunnlag(bidragsmottaker: Rolle): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag(bidragsmottaker)
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        fun Underholdskostnad.hentBidragsmottaker() =
            if (rolle != null && rolle?.rolletype == Rolletype.BIDRAGSMOTTAKER) {
                rolle
            } else {
                rolle?.bidragsmottaker ?: bidragsmottaker
            }!!
        val barnUtenPerioder =
            underholdskostnader
                .filter { it.gjelderAndreBarn && it.faktiskeTilsynsutgifter.isEmpty() }
                .map { u ->
                    personobjekter.hentPerson(u.personIdent) ?: u.opprettPersonGrunnlag(u.hentBidragsmottaker())
                }
        return (
            underholdskostnader
                .flatMap { u ->
                    u.faktiskeTilsynsutgifter.map {
                        val uBidragsmottaker = u.hentBidragsmottaker()
                        val underholdRolle = u.rolle?.takeIf { it.rolletype == Rolletype.BARN }
                        val gjelderBarn =
                            if (underholdRolle != null) {
                                underholdRolle.tilGrunnlagPerson().also {
                                    grunnlagslistePersoner.add(it)
                                }
                            } else if (u.personIdent != null) {
                                personobjekter
                                    .sortedByDescending {
                                        listOf(Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER).indexOf(it.type)
                                    }.hentPerson(u.personIdent) ?: u.opprettPersonGrunnlag(uBidragsmottaker)
                            } else {
                                u.opprettPersonGrunnlag(uBidragsmottaker)
                            }

                        val gjelderBarnReferanse = gjelderBarn.referanse
                        GrunnlagDto(
                            referanse = it.tilGrunnlagsreferanseFaktiskTilsynsutgift(gjelderBarnReferanse),
                            type = Grunnlagstype.FAKTISK_UTGIFT_PERIODE,
                            gjelderReferanse = uBidragsmottaker!!.tilGrunnlagsreferanse(),
                            gjelderBarnReferanse = gjelderBarnReferanse,
                            innhold =
                                POJONode(
                                    FaktiskUtgiftPeriode(
                                        periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                        fødselsdatoBarn = gjelderBarn.personObjekt.fødselsdato,
                                        kostpengerBeløp = it.kostpenger ?: BigDecimal.ZERO,
                                        faktiskUtgiftBeløp = it.tilsynsutgift,
                                        kommentar = it.kommentar,
                                        manueltRegistrert = true,
                                    ),
                                ),
                        )
                    }
                } + grunnlagslistePersoner + barnUtenPerioder
        ).toSet().toList()
    }

    fun finnFødselsdato(
        ident: String?,
        fødselsdato: LocalDate?,
    ): LocalDate? =
        if (fødselsdato == null && ident != null) {
            personService.hentPersonFødselsdato(ident)
        } else {
            fødselsdato
        }
}
