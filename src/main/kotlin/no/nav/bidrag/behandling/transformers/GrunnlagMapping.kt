package no.nav.bidrag.behandling.transformers

import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarn
import no.nav.bidrag.behandling.database.datamodell.Husstandsbarnperiode
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Kilde
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.manglerBosstatus
import no.nav.bidrag.behandling.manglerRolle
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagPeriodeInnhold
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

val GrunnlagDto.personObjekt get() = commonObjectmapper.treeToValue(innhold, Person::class.java)!!
val GrunnlagDto.personIdent get() = personObjekt.ident!!.verdi

fun Behandling.oppretteGrunnlagForHusstandsbarn(søknadsbarnIdent: String?): Set<GrunnlagDto> {
    return husstandsbarn.filter { barn -> barn.ident != søknadsbarnIdent }
        .map(Husstandsbarn::tilPersonGrunnlag).toSet()
}

fun RelatertPersonGrunnlagDto.tilPersonGrunnlag(index: Int): GrunnlagDto {
    val personnavn = navn ?: hentPersonVisningsnavn(relatertPersonPersonId)

    return GrunnlagDto(
        referanse =
            Grunnlagstype.PERSON_HUSSTANDSMEDLEM.tilPersonreferanse(
                fødselsdato?.toCompactString() ?: LocalDate.MIN.toCompactString(),
                index,
            ),
        type = Grunnlagstype.PERSON_HUSSTANDSMEDLEM,
        innhold =
            POJONode(
                Person(
                    ident = relatertPersonPersonId?.let { Personident(it) },
                    navn = if (relatertPersonPersonId.isNullOrEmpty()) personnavn else null,
                    fødselsdato =
                        finnFødselsdato(
                            relatertPersonPersonId,
                            fødselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(-1),
                ).valider(),
            ),
    )
}

fun Rolle.tilPersonGrunnlag(): GrunnlagDto {
    val grunnlagstype = rolletype.tilGrunnlagstype()
    return GrunnlagDto(
        referanse = grunnlagstype.tilPersonreferanse(foedselsdato.toCompactString(), id!!.toInt()),
        type = grunnlagstype,
        innhold =
            POJONode(
                Person(
                    ident = ident?.let { Personident(it) },
                    navn = if (ident == null) navn ?: hentPersonVisningsnavn(ident) else null,
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

fun Behandling.tilGrunnlagSivilstand(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    return this.sivilstand.map {
        GrunnlagDto(
            grunnlagsreferanseListe = listOf(gjelder.referanse),
            referanse = "sivilstand_${gjelder.referanse}_${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.SIVILSTAND_PERIODE,
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

fun Behandling.tilGrunnlagBostatus(grunnlagBarn: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return grunnlagBarn.flatMap {
        val barn: Person = it.innholdTilObjekt()
        val bostatusperioderForBarn =
            this.husstandsbarn.find { hb -> hb.ident == barn.ident?.verdi }
                ?: manglerBosstatus(id!!)
        oppretteGrunnlagForBostatusperioder(it.referanse, bostatusperioderForBarn.perioder)
    }.toSet()
}

fun Behandling.tilGrunnlagInntekt(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto? = null,
): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personObjekt.ident!!
    val søknadsbarnIdent = søknadsbarn?.personObjekt?.ident?.verdi

    return inntekter.asSequence()
        .filtrerEtterGjelderOgBarn(personidentGjelder.verdi, søknadsbarnIdent)
        .map {
            it.tilInntektsrapporteringPeriode(gjelder, søknadsbarn)
        }.toSet()
}

fun Set<GrunnlagDto>.hentGrunnlagSomInneholderPeriode(periode: ÅrMånedsperiode) =
    filter { grunnlag ->
        val grunnlagPeriode: ÅrMånedsperiode? =
            grunnlag.innhold.get(GrunnlagPeriodeInnhold::periode.name)
                ?.let { commonObjectmapper.treeToValue(it) }
        grunnlagPeriode?.let { periode.omsluttesAv(it) } ?: true
    }

fun Husstandsbarn.tilPersonGrunnlag(): GrunnlagDto {
    val rolle = this.behandling.roller.find { it.ident == ident }
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
                    ident = ident?.let { Personident(it) },
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
) = GrunnlagDto(
    type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
    // Ta med gjelder referanse fordi samme type inntekt med samme datoFom kan inkluderes for BM/BP/BA
    referanse = tilGrunnlagreferanse(gjelder),
    // Liste med referanser fra bidrag-inntekt
    grunnlagsreferanseListe = listOf(""),
    gjelderReferanse = gjelder.referanse,
    innhold =
        POJONode(
            InntektsrapporteringPeriode(
                beløp = belop,
                periode = ÅrMånedsperiode(datoFom, datoTom?.plusDays(1)),
                inntektsrapportering = inntektsrapportering,
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
                    when (inntektsrapportering) {
                        Inntektsrapportering.KONTANTSTØTTE, Inntektsrapportering.BARNETILLEGG -> søknadsbarn?.referanse
                        else -> null
                    },
            ),
        ),
)

private fun Sequence<Inntekt>.filtrerEtterGjelderOgBarn(
    gjelderIdent: String,
    barnIdent: String? = null,
) = filter { i -> i.ident == gjelderIdent && (i.gjelderBarn == null || barnIdent == null || i.gjelderBarn == barnIdent) }

private fun Rolletype.tilGrunnlagstype() =
    when (this) {
        Rolletype.BIDRAGSPLIKTIG -> Grunnlagstype.PERSON_BIDRAGSPLIKTIG
        Rolletype.BARN -> Grunnlagstype.PERSON_SØKNADSBARN
        Rolletype.BIDRAGSMOTTAKER -> Grunnlagstype.PERSON_BIDRAGSMOTTAKER
        Rolletype.REELMOTTAKER -> Grunnlagstype.PERSON_REELL_MOTTAKER
        else -> manglerRolle(this)
    }

private fun finnFødselsdato(
    ident: String?,
    fødselsdato: LocalDate?,
): LocalDate? {
    return if (fødselsdato == null && ident != null) {
        hentPersonFødselsdato(ident)
    } else {
        fødselsdato
    }
}

private fun oppretteGrunnlagForBostatusperioder(
    personreferanse: String,
    husstandsbarnperioder: Set<Husstandsbarnperiode>,
): Set<GrunnlagDto> =
    husstandsbarnperioder.map {
        GrunnlagDto(
            grunnlagsreferanseListe = listOf(personreferanse),
            referanse = "bostatus_${personreferanse}_${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.BOSTATUS_PERIODE,
            innhold =
                POJONode(
                    BostatusPeriode(
                        bostatus = it.bostatus,
                        manueltRegistrert = it.kilde == Kilde.MANUELL,
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
        "inntekt_${inntektsrapportering}_${gjelder.referanse}_ba_${gjelderBarn}_${datoFom.toCompactString()}"
    } else {
        "inntekt_${inntektsrapportering}_${gjelder.referanse}_${datoFom.toCompactString()}"
    }

fun Person.valider(rolle: Rolletype? = null): Person {
    if ((ident == null || ident!!.verdi.isEmpty()) && !navn.isNullOrEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Person med fødselsdato $fødselsdato og rolle $rolle mangler ident men har ikke navn. Ident eller Navn må være satt",
        )
    }
    return this
}
