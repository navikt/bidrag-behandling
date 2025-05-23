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
import no.nav.bidrag.behandling.transformers.grunnlag.tilBeregnetInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetAndreBarnTilBidragsmottaker
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetArbeidsforhold
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagUnderholdskostnad
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetSivilstand
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.vedtak.opprettPersonBarnBPBMReferanse
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
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
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
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

@Component
class BehandlingTilGrunnlagMappingV2(
    val personService: PersonService,
    private val beregnSamværsklasseApi: BeregnSamværsklasseApi,
) {
    fun Behandling.tilPersonobjekter(søknadsbarnRolle: Rolle? = null): MutableSet<GrunnlagDto> {
        val søknadsbarnListe =
            søknadsbarnRolle?.let { listOf(it.tilGrunnlagPerson()) }
                ?: søknadsbarn.map { it.tilGrunnlagPerson() }
        return (
            listOf(
                bidragsmottaker?.tilGrunnlagPerson(),
                bidragspliktig?.tilGrunnlagPerson(),
            ) + søknadsbarnListe
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
            sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetHusstandsmedlemmer(personobjekter)
        val innhentetAndreBarnTilBM = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetAndreBarnTilBidragsmottaker(personobjekter)
        val beregnetInntekt = sortertGrunnlagsListeBearbeidet.tilBeregnetInntekt(personobjekter)
        val innhentetInntekter = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetGrunnlagInntekt(personobjekter)
        val innhentetUnderholdskostnad = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetGrunnlagUnderholdskostnad(personobjekter)

        return innhentetInntekter + innhentetArbeidsforhold + innhentetHusstandsmedlemmer +
            innhentetSivilstand + beregnetInntekt +
            innhentetUnderholdskostnad + innhentetAndreBarnTilBM
    }

    fun Rolle.tilGrunnlagsreferanse() = rolletype.tilGrunnlagstype().tilPersonreferanse(fødselsdato.toCompactString(), id!!.toInt())

    fun Rolle.tilGrunnlagPerson(): GrunnlagDto {
        val grunnlagstype = rolletype.tilGrunnlagstype()
        return GrunnlagDto(
            referanse = tilGrunnlagsreferanse(),
            type = grunnlagstype,
            innhold =
                POJONode(
                    Person(
                        ident = ident.takeIf { !it.isNullOrEmpty() }?.let { personService.hentNyesteIdent(it) ?: Personident(it) },
                        navn = if (ident.isNullOrEmpty()) navn ?: personService.hentPersonVisningsnavn(ident) else null,
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
                            personobjekter.hentPersonNyesteIdent(it.rolle!!.ident) ?: it.opprettPersonGrunnlag()
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

    fun Behandling.tilPrivatAvtaleGrunnlag(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun PrivatAvtale.tilPersonGrunnlag(): GrunnlagDto =
            GrunnlagDto(
                referanse =
                    person.opprettPersonBarnBPBMReferanse(type = Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG),
                grunnlagsreferanseListe = emptyList(),
                type = Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG,
                innhold =
                    POJONode(
                        Person(
                            ident = person.ident?.let { Personident(it) },
                            navn = if (person.ident.isNullOrEmpty()) person.navn else null,
                            fødselsdato = person.fødselsdato,
                        ).valider(),
                    ),
            )

        fun PrivatAvtale.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        return privatAvtale
            .flatMap { pa ->
                val underholdRolle = pa.barnetsRolleIBehandlingen
                val gjelderBarn =
                    underholdRolle?.tilGrunnlagPerson()?.also {
                        grunnlagslistePersoner.add(it)
                    } ?: personobjekter.hentPerson(pa.person.ident) ?: pa.opprettPersonGrunnlag()
                val gjelderBarnReferanse = gjelderBarn.referanse
                pa.perioder.map {
                    GrunnlagDto(
                        type = Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG,
                        referanse = it.tilGrunnlagsreferansPrivatAvtalePeriode(gjelderBarnReferanse),
                        gjelderReferanse = personobjekter.bidragspliktig!!.referanse,
                        gjelderBarnReferanse = gjelderBarn.referanse,
                        innhold =
                            POJONode(
                                PrivatAvtalePeriodeGrunnlag(
                                    periode = ÅrMånedsperiode(it.fom, it.tom?.plusDays(1)),
                                    beløp = it.beløp,
                                ),
                            ),
                    )
                } +
                    GrunnlagDto(
                        referanse = pa.tilGrunnlagsreferansPrivatAvtale(gjelderBarnReferanse),
                        gjelderReferanse = personobjekter.bidragspliktig!!.referanse,
                        gjelderBarnReferanse = gjelderBarnReferanse,
                        type = Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG,
                        innhold =
                            POJONode(
                                PrivatAvtaleGrunnlag(
                                    avtaleInngåttDato = pa.avtaleDato ?: virkningstidspunkt!!,
                                    avtaleType = pa.avtaleType ?: PrivatAvtaleType.PRIVAT_AVTALE,
                                    skalIndeksreguleres = pa.skalIndeksreguleres,
                                ),
                            ),
                    )
            }.toSet()
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
                        søknadsbarn == null &&
                            (
                                it.gjelderBarn.isNullOrEmpty() ||
                                    // Ikke ta med inntekter som ikke gjelder noen av søknadsbarna. Kan feks skje hvis en søknadsbarn er fjernet fra behandling
                                    alleSøknadsbarnIdenter.contains(it.gjelderBarn)
                            ) ||
                            søknadsbarn != null &&
                            (it.gjelderBarn == søknadsbarn.personIdent || it.gjelderBarn.isNullOrEmpty())
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
        return GrunnlagDto(
            referanse =
                grunnlagstype.tilPersonreferanse(
                    rolle?.fødselsdato?.toCompactString() ?: fødselsdato.toCompactString(),
                    (rolle?.id ?: id!!).toInt(),
                ),
            type = grunnlagstype,
            innhold =
                POJONode(
                    Person(
                        ident = if (!ident.isNullOrEmpty()) Personident(ident!!) else null,
                        navn = if (ident.isNullOrEmpty()) navn ?: personService.hentPersonVisningsnavn(ident) else null,
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

        fun Underholdskostnad.tilPersonGrunnlag(): GrunnlagDto =
            GrunnlagDto(
                referanse =
                    opprettPersonBarnBPBMReferanse(
                        type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
                        person.fødselsdato,
                        person.ident,
                        person.navn,
                    ),
                grunnlagsreferanseListe =
                    if (kilde == Kilde.OFFENTLIG) {
                        listOf(
                            opprettInnhentetAnderBarnTilBidragsmottakerGrunnlagsreferanse(
                                behandling.bidragsmottaker!!.tilGrunnlagsreferanse(),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                type = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
                innhold =
                    POJONode(
                        Person(
                            ident = person.ident?.let { Personident(it) },
                            navn = if (person.ident.isNullOrEmpty()) person.navn else null,
                            fødselsdato = person.fødselsdato,
                        ).valider(),
                    ),
            )

        fun Underholdskostnad.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        val barnUtenPerioder =
            underholdskostnader
                .filter { it.barnetsRolleIBehandlingen == null && it.faktiskeTilsynsutgifter.isEmpty() }
                .map { u ->
                    personobjekter.hentPerson(u.person.ident) ?: u.opprettPersonGrunnlag()
                }
        return (
            underholdskostnader
                .flatMap { u ->
                    u.faktiskeTilsynsutgifter.map {
                        val underholdRolle = u.barnetsRolleIBehandlingen
                        val gjelderBarn =
                            underholdRolle?.tilGrunnlagPerson()?.also {
                                grunnlagslistePersoner.add(it)
                            } ?: personobjekter.hentPerson(u.person.ident) ?: u.opprettPersonGrunnlag()
                        val gjelderBarnReferanse = gjelderBarn.referanse
                        GrunnlagDto(
                            referanse = it.tilGrunnlagsreferanseFaktiskTilsynsutgift(gjelderBarnReferanse),
                            type = Grunnlagstype.FAKTISK_UTGIFT_PERIODE,
                            gjelderReferanse = bidragsmottaker!!.tilGrunnlagsreferanse(),
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

    fun Collection<GrunnlagDto>.hentPersonNyesteIdent(ident: String?) =
        filter { it.erPerson() }.find { it.personIdent == personService.hentNyesteIdent(ident)?.verdi || it.personIdent == ident }

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
