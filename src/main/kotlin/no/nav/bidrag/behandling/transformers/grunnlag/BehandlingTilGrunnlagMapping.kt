package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.vedtak.hentPersonNyesteIdent
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.behandling.transformers.vedtak.inntektsrapporteringSomKreverSøknadsbarn
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetHusstandsmedlemGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettInnhentetSivilstandGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

fun Behandling.tilGrunnlagSivilstand(gjelder: BaseGrunnlag): Set<GrunnlagDto> {
    return sivilstand.map {
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

fun Behandling.byggInnhentetGrunnlag(personobjekter: MutableSet<GrunnlagDto>): Set<GrunnlagDto> {
    val innhentetArbeidsforhold = grunnlagListe.tilInnhentetArbeidsforhold(personobjekter)
    val innhentetSivilstand = grunnlagListe.tilInnhentetSivilstand(personobjekter)
    val innhentetHusstandsmedlemmer = grunnlagListe.tilInnhentetHusstandsmedlemmer(personobjekter)
    val beregnetInntekt = grunnlagListe.tilBeregnetInntekt(personobjekter)
    val innhentetInntekter = grunnlagListe.tilInnhentetGrunnlagInntekt(personobjekter)

    return innhentetInntekter + innhentetArbeidsforhold + innhentetHusstandsmedlemmer + innhentetSivilstand + beregnetInntekt
}

fun Rolle.tilGrunnlagPerson(): GrunnlagDto {
    val grunnlagstype = rolletype.tilGrunnlagstype()
    return GrunnlagDto(
        referanse =
            grunnlagstype.tilPersonreferanse(foedselsdato.toCompactString(), id!!.toInt()),
        type = grunnlagstype,
        innhold =
            POJONode(
                Person(
                    ident = ident.takeIf { !it.isNullOrEmpty() }?.let { hentNyesteIdent(it) },
                    navn = if (ident.isNullOrEmpty()) navn ?: hentPersonVisningsnavn(ident) else null,
                    fødselsdato =
                        finnFødselsdato(
                            ident,
                            foedselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id ?: -1),
                ).valider(rolletype),
            ),
    )
}

fun Behandling.tilGrunnlagBostatus(personobjekter: Set<GrunnlagDto>): Set<GrunnlagDto> {
    val personobjekterHusstandsbarn = mutableSetOf<GrunnlagDto>()

    fun Husstandsbarn.opprettPersonGrunnlag(): GrunnlagDto {
        val relatertPersonGrunnlag = tilGrunnlagPerson()
        personobjekterHusstandsbarn.add(relatertPersonGrunnlag)
        return relatertPersonGrunnlag
    }

    val grunnlagBosstatus =
        husstandsbarn.flatMap {
            val barn = personobjekter.hentPersonNyesteIdent(it.ident) ?: it.opprettPersonGrunnlag()
            val part =
                (if (stonadstype == Stønadstype.FORSKUDD) personobjekter.bidragsmottaker else personobjekter.bidragspliktig)
            opprettGrunnlagForBostatusperioder(
                barn.referanse,
                part!!.referanse,
                it.perioder,
            )
        }.toSet()

    return grunnlagBosstatus + personobjekterHusstandsbarn
}

fun Behandling.tilGrunnlagInntekt(
    personobjekter: Set<GrunnlagDto>,
    søknadsbarn: GrunnlagDto? = null,
    inkluderAlle: Boolean = true,
): Set<GrunnlagDto> {
    return inntekter.asSequence()
        .filter { personobjekter.hentPersonNyesteIdent(it.ident) != null && (inkluderAlle || it.taMed) }
        .groupBy { it.ident }
        .flatMap { (ident, innhold) ->
            val gjelder = personobjekter.hentPersonNyesteIdent(ident)!!
            innhold.filter { søknadsbarn == null || it.gjelderBarn == søknadsbarn.personIdent || it.gjelderBarn.isNullOrEmpty() }
                .groupBy { it.gjelderBarn }
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

fun Husstandsbarn.tilGrunnlagPerson(): GrunnlagDto {
    val rolle = behandling.roller.find { it.ident == ident }
    val grunnlagstype = rolle?.rolletype?.tilGrunnlagstype() ?: Grunnlagstype.PERSON_HUSSTANDSMEDLEM
    return GrunnlagDto(
        referanse =
            grunnlagstype.tilPersonreferanse(
                foedselsdato.toCompactString(),
                (rolle?.id ?: id!!).toInt(),
            ),
        type = grunnlagstype,
        innhold =
            POJONode(
                Person(
                    ident = ident.takeIf { !it.isNullOrEmpty() }?.let { hentNyesteIdent(it) },
                    navn = if (ident.isNullOrEmpty()) navn ?: hentPersonVisningsnavn(ident) else null,
                    fødselsdato =
                        finnFødselsdato(
                            ident,
                            foedselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id ?: -1),
                ).valider(),
            ),
    )
}

private fun Inntekt.tilInntektsrapporteringPeriode(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto?,
    grunnlagListe: List<Grunnlag> = emptyList(),
) = GrunnlagDto(
    type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
    referanse = tilGrunnlagreferanse(gjelder),
    // Liste med referanser fra bidrag-inntekt
    grunnlagsreferanseListe =
        grunnlagListe.hentGrunnlagsreferanserForInntekt(
            gjelder.personIdent!!,
            this,
        ),
    gjelderReferanse = gjelder.referanse,
    innhold =
        POJONode(
            InntektsrapporteringPeriode(
                beløp = belop,
                versjon = (kilde == Kilde.OFFENTLIG).ifTrue { grunnlagListe.hentVersjonForInntekt(this) },
                periode = ÅrMånedsperiode(datoFom, datoTom?.plusDays(1)),
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
                valgt = taMed,
                inntekstpostListe =
                    inntektsposter.map {
                        InntektsrapporteringPeriode.Inntektspost(
                            beløp = it.beløp,
                            inntekstype = it.inntektstype,
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

fun finnFødselsdato(
    ident: String?,
    fødselsdato: LocalDate?,
): LocalDate? {
    return if (fødselsdato == null && ident != null) {
        hentPersonFødselsdato(ident)
    } else {
        fødselsdato
    }
}

private fun opprettGrunnlagForBostatusperioder(
    barnreferanse: String,
    relatertTilPartReferanse: String,
    husstandsbarnperioder: Set<Husstandsbarnperiode>,
): Set<GrunnlagDto> =
    husstandsbarnperioder.map {
        GrunnlagDto(
            referanse = "bostatus_${barnreferanse}_${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.BOSTATUS_PERIODE,
            gjelderReferanse = barnreferanse,
            grunnlagsreferanseListe =
                if (it.kilde == Kilde.OFFENTLIG) {
                    listOf(
                        opprettInnhentetHusstandsmedlemGrunnlagsreferanse(
                            relatertTilPartReferanse,
                            referanseRelatertTil = barnreferanse,
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

private fun Inntekt.tilGrunnlagreferanse(gjelder: GrunnlagDto) =
    if (gjelderBarn != null) {
        "inntekt_${type}_${gjelder.referanse}_ba_${gjelderBarn}_${datoFom.toCompactString()}"
    } else {
        "inntekt_${type}_${gjelder.referanse}_${datoFom.toCompactString()}"
    }

fun Person.valider(rolle: Rolletype? = null): Person {
    if ((ident == null || ident!!.verdi.isEmpty()) && navn.isNullOrEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Person med fødselsdato $fødselsdato og rolle $rolle mangler ident men har ikke navn. Ident eller Navn må være satt",
        )
    }
    return this
}
