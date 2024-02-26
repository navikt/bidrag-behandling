package no.nav.bidrag.behandling.utils.testdata

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.domene.enums.rolle.Rolletype
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
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import java.time.LocalDate
import java.time.LocalDateTime

val testdataGrunnlagInnhentetTidspunkt = LocalDateTime.parse("2024-01-01T00:00:00")

fun opprettGrunnlagFraFil(
    behandling: Behandling,
    filnavn: String,
    type: Grunnlagsdatatype,
): List<Grunnlag> {
    val fil = hentFil("/__files/$filnavn")
    val grunnlag: HentGrunnlagDto = commonObjectmapper.readValue(fil)

    return when (type) {
        Grunnlagsdatatype.HUSSTANDSMEDLEMMER -> {
            grunnlag.husstandsmedlemmerOgEgneBarnListe.tilGrunnlagEntity(behandling)
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

        Grunnlagsdatatype.KONTANTSTØTTE ->
            grunnlag.kontantstøtteListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.SMÅBARNSTILLEGG ->
            grunnlag.småbarnstilleggListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.UTVIDET_BARNETRYGD ->
            grunnlag.utvidetBarnetrygdListe.tilGrunnlagEntity(behandling)

        Grunnlagsdatatype.INNTEKT -> {
            behandling.opprettGrunnlagEntityForInntekt(
                grunnlag.ainntektListe,
                grunnlag.skattegrunnlagListe,
            )
        }

        else -> emptyList()
    }
}

@JvmName("relatertPersonGrunnlagDtoTilGrunnlagEntity")
fun List<RelatertPersonGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(
                Grunnlagsdatatype.HUSSTANDSMEDLEMMER,
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
            behandling.opprettGrunnlag(Grunnlagsdatatype.BARNETILLEGG, grunnlag, partPersonId)
        }

@JvmName("barnetilsynGrunnlagDtoTilGrunnlagEntity")
fun List<BarnetilsynGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.BARNETILSYN, grunnlag, partPersonId)
        }

@JvmName("kontantstøtteGrunnlagDtoTilGrunnlagEntity")
fun List<KontantstøtteGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.partPersonId }
        .map { (partPersonId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.KONTANTSTØTTE, grunnlag, partPersonId)
        }

@JvmName("småbarnstilleggGrunnlagDtoTilGrunnlagEntity")
fun List<SmåbarnstilleggGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.personId }
        .map { (personId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.SMÅBARNSTILLEGG, grunnlag, personId)
        }

@JvmName("utvidetBarnetrygdGrunnlagDtoTilGrunnlagEntity")
fun List<UtvidetBarnetrygdGrunnlagDto>.tilGrunnlagEntity(behandling: Behandling) =
    groupBy { it.personId }
        .map { (personId, grunnlag) ->
            behandling.opprettGrunnlag(Grunnlagsdatatype.UTVIDET_BARNETRYGD, grunnlag, personId)
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
            Grunnlagsdatatype.INNTEKT,
            GrunnlagInntekt(
                ainntekt = ainntektListe,
                skattegrunnlag = skattegrunnlagListe,
            ),
            it,
        )
    }
}

fun Behandling.opprettGrunnlag(
    type: Grunnlagsdatatype,
    grunnlag: Any,
    personId: String,
): Grunnlag {
    return Grunnlag(
        behandling = this,
        type = type,
        data = commonObjectmapper.writeValueAsString(grunnlag),
        innhentet = testdataGrunnlagInnhentetTidspunkt,
        rolle =
            roller.find { it.ident == personId } ?: Rolle(
                ident = personId,
                behandling = this,
                rolletype = Rolletype.FEILREGISTRERT,
                foedselsdato = LocalDate.parse("2020-01-01"),
            ),
    )
}
