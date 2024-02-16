package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
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
import no.nav.bidrag.behandling.transformers.toCompactString
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BostatusPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.InntektsrapporteringPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.SivilstandPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

fun Behandling.tilGrunnlagSivilstand(gjelder: GrunnlagDto): Set<GrunnlagDto> {
    return sivilstand.map {
        GrunnlagDto(
            referanse = "sivilstand_${gjelder.referanse}_${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.SIVILSTAND_PERIODE,
            gjelderReferanse = gjelder.referanse,
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
    val øvrigeBarnIHusstand = opprettGrunnlagForHusstandsbarn()
    val bm = tilPersonobjektBidragsmottaker()
    val søknadsbarnListe =
        søknadsbarnRolle?.let { listOf(it.tilGrunnlagPerson()) }
            ?: søknadsbarn.map { it.tilGrunnlagPerson() }
    return (listOf(bm) + søknadsbarnListe + øvrigeBarnIHusstand).toMutableSet()
}

fun Behandling.tilPersonobjektBidragsmottaker(): GrunnlagDto {
    return bidragsmottaker?.tilGrunnlagPerson()
        ?: manglerRolle(Rolletype.BIDRAGSMOTTAKER, id!!)
}

fun Behandling.byggInnhentetGrunnlag(
    søknadsbarn: GrunnlagDto,
    personobjekter: MutableSet<GrunnlagDto>,
): Set<GrunnlagDto> {
    // TODO: Legg til logikk for innhenting av grunnlag for bidragspliktig når vi kommer til Bidrag

    val bidragsmottaker = personobjekter.bidragsmottaker
    val grunnlagSøknadsbarn =
        grunnlagListe.tilInnhentetArbeidsforhold(søknadsbarn) +
            grunnlagListe.tilInnhentetGrunnlagInntekt(
                bidragsmottaker,
                søknadsbarn,
            ) +
            grunnlagListe.tilInnhentetGrunnlagInntektBarn(søknadsbarn)

    val innhentetHusstandsmedlemmerBm =
        grunnlagListe.tilInnhentetHusstandsmedlemmer(bidragsmottaker, personobjekter)

    val innhentetSivilstandBm = grunnlagListe.tilInnhentetSivilstand(bidragsmottaker)

    val innhentetArbeidsforholdBm =
        grunnlagListe.tilInnhentetArbeidsforhold(bidragsmottaker)

    val beregnetInntekt =
        grunnlagListe.tilBeregnetInntekt(bidragsmottaker) +
            grunnlagListe.tilBeregnetInntekt(
                søknadsbarn,
            )

    return grunnlagSøknadsbarn + innhentetArbeidsforholdBm + innhentetHusstandsmedlemmerBm + innhentetSivilstandBm + beregnetInntekt
}

fun Behandling.opprettGrunnlagForHusstandsbarn(søknadsbarn: GrunnlagDto? = null): Set<GrunnlagDto> {
    val søknadsbarnIdenter =
        søknadsbarn?.let { listOf(it.personIdent) }
            ?: this.roller.filter { it.rolletype == Rolletype.BARN }.map { it.ident }
    return husstandsbarn.filter { barn -> !søknadsbarnIdenter.contains(barn.ident) }
        .map(Husstandsbarn::tilGrunnlagPerson).toSet()
}

fun Rolle.tilGrunnlagPerson(): GrunnlagDto {
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

fun Behandling.tilGrunnlagBostatus(grunnlagBarn: Set<GrunnlagDto>): Set<GrunnlagDto> {
    return grunnlagBarn.flatMap {
        val barn: Person = it.innholdTilObjekt()
        val bostatusperioderForBarn =
            husstandsbarn.find { hb -> hb.ident == barn.ident?.verdi }
                ?: manglerBosstatus(it.personIdent)
        oppretteGrunnlagForBostatusperioder(it.referanse, bostatusperioderForBarn.perioder)
    }.toSet()
}

fun Behandling.tilGrunnlagInntekt(
    gjelder: GrunnlagDto,
    søknadsbarn: GrunnlagDto? = null,
): Set<GrunnlagDto> {
    val personidentGjelder = gjelder.personIdent
    val søknadsbarnIdent = søknadsbarn?.personIdent

    return inntekter.asSequence()
        .filtrerEtterGjelderOgBarn(personidentGjelder, søknadsbarnIdent)
        .map {
            it.tilInntektsrapporteringPeriode(gjelder, søknadsbarn, grunnlagListe)
        }.toSet()
}

fun Husstandsbarn.tilGrunnlagPerson(): GrunnlagDto {
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
    grunnlagListe: List<BehandlingGrunnlag> = emptyList(),
) = GrunnlagDto(
    type = Grunnlagstype.INNTEKT_RAPPORTERING_PERIODE,
    // Ta med gjelder referanse fordi samme type inntekt med samme datoFom kan inkluderes for BM/BP/BA
    referanse = tilGrunnlagreferanse(gjelder),
    // Liste med referanser fra bidrag-inntekt
    grunnlagsreferanseListe =
        grunnlagListe.hentGrunnlagsreferanserForInntekt(
            gjelder.personIdent,
            this,
        ),
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

private fun Behandling.oppretteGrunnlagForBostatusperioder(
    personreferanse: String,
    husstandsbarnperioder: Set<Husstandsbarnperiode>,
): Set<GrunnlagDto> =
    husstandsbarnperioder.map {
        GrunnlagDto(
            referanse = "bostatus_${personreferanse}_${it.datoFom?.toCompactString()}",
            type = Grunnlagstype.BOSTATUS_PERIODE,
            gjelderReferanse = personreferanse,
            grunnlagsreferanseListe =
                grunnlagListe.hentGrunnlagsreferanserForHusstandsmedlem(
                    personreferanse,
                    it.husstandsbarn.ident,
                    it,
                    this,
                ),
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
