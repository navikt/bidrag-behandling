package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteAktiv
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.grunnlag.tilBeregnetInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetArbeidsforhold
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetHusstandsmedlemmer
import no.nav.bidrag.behandling.transformers.grunnlag.tilInnhentetSivilstand
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class BehandlingTilGrunnlagMappingV2(
    val personService: PersonService,
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
                        referanse = "sivilstand_${gjelder.referanse}_${it.datoFom?.toCompactString()}",
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
        val innhentetHusstandsmedlemmer = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetHusstandsmedlemmer(personobjekter)
        val beregnetInntekt = sortertGrunnlagsListeBearbeidet.tilBeregnetInntekt(personobjekter)
        val innhentetInntekter = sortertGrunnlagsListeIkkeBearbeidet.tilInnhentetGrunnlagInntekt(personobjekter)

        return innhentetInntekter + innhentetArbeidsforhold + innhentetHusstandsmedlemmer + innhentetSivilstand + beregnetInntekt
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
                        ident = ident.takeIf { !it.isNullOrEmpty() }?.let { personService.hentNyesteIdent(it) },
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

    fun Behandling.tilGrunnlagInntekt(
        personobjekter: Set<GrunnlagDto>,
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
