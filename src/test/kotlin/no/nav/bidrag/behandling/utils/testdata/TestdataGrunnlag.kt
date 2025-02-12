package no.nav.bidrag.behandling.utils.testdata

import StubUtils
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.GrunnlagDatakilde
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettAinntektGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettBarnetilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettKontantstøtteGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSkattegrunnlagGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettSmåbarnstilleggGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.opprettUtvidetbarnetrygGrunnlagsreferanse
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.HentGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SkattegrunnlagGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.TilleggsstønadGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.Ainntektspost
import no.nav.bidrag.transport.behandling.inntekt.request.Barnetillegg
import no.nav.bidrag.transport.behandling.inntekt.request.Kontantstøtte
import no.nav.bidrag.transport.behandling.inntekt.request.SkattegrunnlagForLigningsår
import no.nav.bidrag.transport.behandling.inntekt.request.Småbarnstillegg
import no.nav.bidrag.transport.behandling.inntekt.request.TransformerInntekterRequest
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygd
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

val testdataGrunnlagInnhentetTidspunkt = LocalDateTime.parse("2024-01-01T00:00:00")

fun opprettGrunnlagFraFil(
    behandling: Behandling,
    fil: String,
    type: Grunnlagsdatatype,
): List<Grunnlag> {
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(fil)

    return when (type) {
        Grunnlagsdatatype.BOFORHOLD -> {
            grunnlag.husstandsmedlemmerOgEgneBarnListe
                .filter {
                    if (behandling.tilType() == TypeBehandling.FORSKUDD) {
                        it.partPersonId == behandling.bidragsmottaker?.ident
                    } else {
                        it.partPersonId == behandling.bidragspliktig?.ident
                    }
                }.tilGrunnlagEntity(behandling)
        }
        Grunnlagsdatatype.ANDRE_BARN -> {
            if (behandling.tilType() == TypeBehandling.BIDRAG) {
                grunnlag.husstandsmedlemmerOgEgneBarnListe
                    .filter { it.partPersonId == behandling.bidragsmottaker!!.ident }
                    .tilGrunnlagEntity(behandling, Grunnlagsdatatype.ANDRE_BARN)
            } else {
                emptyList()
            }
        }
        Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN -> {
            grunnlag.husstandsmedlemmerOgEgneBarnListe.tilGrunnlagEntity(behandling, Grunnlagsdatatype.BOFORHOLD_ANDRE_VOKSNE_I_HUSSTANDEN)
        }

        Grunnlagsdatatype.SIVILSTAND ->
            grunnlag.sivilstandListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.ARBEIDSFORHOLD ->
            grunnlag.arbeidsforholdListe.tilGrunnlagEntity(behandling)
        // Inntekter er en subset av grunnlag så lagrer bare alt
        Grunnlagsdatatype.BARNETILLEGG ->
            grunnlag.barnetilleggListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.BARNETILSYN ->
            grunnlag.barnetilsynListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.TILLEGGSSTØNAD ->
            grunnlag.tilleggsstønadBarnetilsynListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.KONTANTSTØTTE ->
            grunnlag.kontantstøtteListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.SMÅBARNSTILLEGG ->
            grunnlag.småbarnstilleggListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
            grunnlag.utvidetBarnetrygdListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER -> {
            val skattegrunnlagGruppert = grunnlag.skattegrunnlagListe.groupBy { it.personId }
            val ainntektGruppert = grunnlag.ainntektListe.groupBy { it.personId }
            val personIdenter = ainntektGruppert.keys + skattegrunnlagGruppert.keys
            personIdenter
                .map { personId ->
                    behandling.opprettGrunnlag(
                        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                        SkattepliktigeInntekter(
                            ainntekter = ainntektGruppert[personId] ?: emptyList(),
                            skattegrunnlag = skattegrunnlagGruppert[personId] ?: emptyList(),
                        ),
                        personId,
                    )
                }
        }

        else -> emptyList()
    }
}

@JvmName("relatertPersonGrunnlagDtoTilGrunnlagEntity")
fun List<RelatertPersonGrunnlagDto>.tilGrunnlagEntity(
    behandling: Behandling,
    type: Grunnlagsdatatype = Grunnlagsdatatype.BOFORHOLD,
) = groupBy { it.partPersonId }
    .map { (partPersonId, grunnlag) ->
        behandling.opprettGrunnlag(
            type,
            grunnlag,
            partPersonId!!,
        )
    }

@JvmName("sivilstandGrunnlagDtoTilGrunnlagEntity")
fun List<SivilstandGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.personId }
        .map { (personId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.SIVILSTAND, grunnlag, personId!!)
        }

@JvmName("arbeidsforholdGrunnlagDtoTilGrunnlagEntity")
fun List<ArbeidsforholdGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.ARBEIDSFORHOLD, grunnlag, partPersonId)
        }

@JvmName("barnetilleggGrunnlagDtoTilGrunnlagEntity")
fun List<BarnetilleggGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.BARNETILLEGG,
                grunnlag,
                partPersonId,
            )
        }

@JvmName("tilleggsstønadGrunnlagDtoTilGrunnlagEntity")
fun List<TilleggsstønadGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.TILLEGGSSTØNAD,
                grunnlag,
                partPersonId,
            )
        }

@JvmName("barnetilsynGrunnlagDtoTilGrunnlagEntity")
fun List<BarnetilsynGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.BARNETILSYN,
                grunnlag,
                partPersonId,
            )
        }

@JvmName("kontantstøtteGrunnlagDtoTilGrunnlagEntity")
fun List<KontantstøtteGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.KONTANTSTØTTE,
                grunnlag,
                partPersonId,
            )
        }

@JvmName("småbarnstilleggGrunnlagDtoTilGrunnlagEntity")
fun List<SmåbarnstilleggGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.personId }
        .map { (personId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.SMÅBARNSTILLEGG,
                grunnlag,
                personId,
            )
        }

@JvmName("utvidetBarnetrygdGrunnlagDtoTilGrunnlagEntity")
fun List<UtvidetBarnetrygdGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.personId }
        .map { (personId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.UTVIDET_BARNETRYGD,
                grunnlag,
                personId,
            )
        }

fun Behandling.opprettGrunnlagEntityForInntekt(
    ainntektListe: List<AinntektGrunnlagDto>,
    skattegrunnlagListe: List<SkattegrunnlagGrunnlagDto>,
): List<Grunnlag> {
    val ainntektPerPersonMap = ainntektListe.groupBy { it.personId }
    val skattegrunnlagPerPersonMap = skattegrunnlagListe.groupBy { it.personId }

    val personer = ainntektPerPersonMap.keys + skattegrunnlagPerPersonMap.keys
    return personer.map {
        val ainntektListe = ainntektPerPersonMap[it] ?: emptyList()
        val skattegrunnlagListe = skattegrunnlagPerPersonMap[it] ?: emptyList()
        opprettGrunnlag(
            Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
            SkattepliktigeInntekter(
                skattegrunnlag = skattegrunnlagListe,
                ainntekter = ainntektListe,
            ),
            it,
        )
    }
}

fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    personId: String,
    erBearbeidet: Boolean = false,
): Grunnlag =
    Grunnlag(
        behandling = this,
        type = type,
        erBearbeidet = erBearbeidet,
        data = commonObjectmapper.writeValueAsString(grunnlag),
        innhentet = testdataGrunnlagInnhentetTidspunkt,
        aktiv = LocalDateTime.now(),
        rolle =
            roller.find { it.ident == personId } ?: Rolle(
                ident = personId,
                behandling = this,
                rolletype = Rolletype.FEILREGISTRERT,
                fødselsdato = LocalDate.parse("2020-01-01"),
            ),
    )

fun HentGrunnlagDto.tilTransformerInntekterRequest(
    rolle: Rolle,
    fraDato: LocalDate,
) = TransformerInntekterRequest(
    ainntektHentetDato = fraDato,
    ainntektsposter =
        this.ainntektListe.filter { it.personId == rolle.ident }.flatMap { ainntektGrunnlag ->
            ainntektGrunnlag.ainntektspostListe.map {
                Ainntektspost(
                    utbetalingsperiode = it.utbetalingsperiode,
                    opptjeningsperiodeFra = it.opptjeningsperiodeFra,
                    opptjeningsperiodeTil = it.opptjeningsperiodeTil,
                    etterbetalingsperiodeFra = it.etterbetalingsperiodeFra,
                    etterbetalingsperiodeTil = it.etterbetalingsperiodeTil,
                    beskrivelse = it.beskrivelse,
                    beløp = it.beløp,
                    referanse = opprettAinntektGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
                )
            }
        },
    skattegrunnlagsliste =
        this.skattegrunnlagListe.filter { it.personId == rolle.ident }.map {
            SkattegrunnlagForLigningsår(
                ligningsår = it.periodeFra.year,
                skattegrunnlagsposter = it.skattegrunnlagspostListe,
                referanse =
                    opprettSkattegrunnlagGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                        it.periodeFra.year,
                    ),
            )
        },
    barnetilleggsliste =
        this.barnetilleggListe.filter { it.partPersonId == rolle.ident }.map {
            Barnetillegg(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløpBrutto,
                barnPersonId = it.barnPersonId,
                referanse =
                    opprettBarnetilleggGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                        GrunnlagDatakilde.PENSJON,
                    ),
            )
        },
    kontantstøtteliste =
        this.kontantstøtteListe.filter { it.partPersonId == rolle.ident }.map {
            Kontantstøtte(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp.toBigDecimal(),
                barnPersonId = it.barnPersonId,
                referanse =
                    opprettKontantstøtteGrunnlagsreferanse(
                        rolle.tilGrunnlagPerson().referanse,
                    ),
            )
        },
    utvidetBarnetrygdliste =
        this.utvidetBarnetrygdListe.filter { it.personId == rolle.ident }.map {
            UtvidetBarnetrygd(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp,
                referanse = opprettUtvidetbarnetrygGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
            )
        },
    småbarnstilleggliste =
        this.småbarnstilleggListe.filter { it.personId == rolle.ident }.map {
            Småbarnstillegg(
                periodeFra = it.periodeFra,
                periodeTil = it.periodeTil,
                beløp = it.beløp,
                referanse = opprettSmåbarnstilleggGrunnlagsreferanse(rolle.tilGrunnlagPerson().referanse),
            )
        },
)

fun List<Grunnlag>.filtrerEtterTypeOgIdent(
    type: Grunnlagsdatatype,
    ident: String,
    erBearbeidet: Boolean = false,
) = filter { it.type == type && it.rolle.ident == ident && it.erBearbeidet == erBearbeidet }

fun Behandling.initGrunnlagRespons(
    stubUtils: StubUtils,
    bmIdent: String? = bidragsmottaker!!.ident,
    bpIdent: String? = bidragspliktig?.ident,
    baIdent: String? = søknadsbarn.first().ident,
) {
    roller.forEach {
        when (it.rolletype) {
            Rolletype.BIDRAGSMOTTAKER ->

                stubUtils.stubHenteGrunnlag(
                    rolleIdent = bmIdent,
                    responsobjekt =
                        lagGrunnlagsdata(
                            if (tilType() ==
                                TypeBehandling.FORSKUDD
                            ) {
                                "vedtak/vedtak-grunnlagrespons-forskudd-bm.json"
                            } else {
                                "vedtak/vedtak-grunnlagrespons-sb-bm.json"
                            },
                            YearMonth.from(virkningstidspunkt),
                            bmIdent!!,
                            baIdent!!,
                        ),
                )

            Rolletype.BIDRAGSPLIKTIG ->
                stubUtils.stubHenteGrunnlag(
                    rolleIdent = bpIdent,
                    responsobjekt =
                        lagGrunnlagsdata(
                            "vedtak/vedtak-grunnlagrespons-sb-bp.json",
                            YearMonth.from(virkningstidspunkt),
                            bpIdent!!,
                            baIdent!!,
                        ),
                )

            Rolletype.BARN ->
                stubUtils.stubHenteGrunnlag(
                    rolleIdent = baIdent,
                    responsobjekt =
                        lagGrunnlagsdata(
                            "vedtak/vedtak-grunnlagrespons-barn1.json",
                            YearMonth.from(virkningstidspunkt),
                            baIdent!!,
                        ),
                )

            else -> throw Exception()
        }
    }
}
