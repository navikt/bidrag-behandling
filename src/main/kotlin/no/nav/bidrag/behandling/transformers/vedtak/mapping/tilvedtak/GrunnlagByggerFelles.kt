package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.service.NotatService.Companion.henteInntektsnotat
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.behandling.transformers.vedtak.skyldnerNav
import no.nav.bidrag.behandling.transformers.vedtak.takeIfNotNullOrEmpty
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettBehandlingsreferanseRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

val grunnlagsreferanse_delberegning_utgift = "delberegning_utgift"
val grunnlagsreferanse_utgiftsposter = "utgiftsposter"
val grunnlagsreferanse_utgift_direkte_betalt = "utgift_direkte_betalt"
val grunnlagsreferanse_utgift_maks_godkjent_beløp = "utgift_maks_godkjent_beløp"
val grunnlagsreferanse_løpende_bidrag = "løpende_bidrag_bidragspliktig"

fun GrunnlagDto.tilOpprettRequestDto() =
    OpprettGrunnlagRequestDto(
        referanse = referanse,
        type = type,
        innhold = innhold,
        grunnlagsreferanseListe = grunnlagsreferanseListe,
        gjelderReferanse = gjelderReferanse,
    )

private fun opprettGrunnlagNotat(
    notatType: Notattype,
    medIVedtak: Boolean,
    innhold: String,
    gjelderReferanse: String? = null,
) = GrunnlagDto(
    referanse = "notat_${notatType}_${if (medIVedtak) "med_i_vedtaket" else "kun_i_notat"}${gjelderReferanse?.let { "_$it" } ?: ""}",
    type = Grunnlagstype.NOTAT,
    gjelderReferanse = gjelderReferanse,
    innhold =
        POJONode(
            NotatGrunnlag(
                innhold = innhold,
                erMedIVedtaksdokumentet = medIVedtak,
                type = notatType,
            ),
        ),
)

fun Behandling.byggGrunnlagSøknad() =
    setOf(
        GrunnlagDto(
            referanse = "søknad",
            type = Grunnlagstype.SØKNAD,
            innhold =
                POJONode(
                    SøknadGrunnlag(
                        klageMottattDato = klageMottattdato,
                        mottattDato = mottattdato,
                        søktFraDato = søktFomDato,
                        søktAv = soknadFra,
                        opprinneligVedtakstype = opprinneligVedtakstype,
                    ),
                ),
        ),
    )

fun Behandling.byggGrunnlagVirkningsttidspunkt() =
    setOf(
        GrunnlagDto(
            referanse = "virkningstidspunkt",
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

fun Behandling.byggGrunnlagNotaterDirekteAvslag(): Set<GrunnlagDto> =
    setOf(
        henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT)?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(Notattype.VIRKNINGSTIDSPUNKT, false, it)
        },
        henteNotatinnhold(this, Notattype.UTGIFTER)?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(Notattype.UTGIFTER, false, it)
        },
    ).filterNotNull().toSet()

fun Behandling.byggGrunnlagNotater(): Set<GrunnlagDto> {
    val notatGrunnlag =
        setOf(
            henteNotatinnhold(this, Notattype.VIRKNINGSTIDSPUNKT)?.takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.VIRKNINGSTIDSPUNKT, false, it)
            },
            henteNotatinnhold(this, Notattype.BOFORHOLD)?.takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.BOFORHOLD, false, it)
            },
            henteNotatinnhold(this, Notattype.UTGIFTER)?.takeIfNotNullOrEmpty {
                opprettGrunnlagNotat(Notattype.UTGIFTER, false, it)
            },
        ).filterNotNull()

    val notatGrunnlagInntekter =
        roller
            // TODO: Midlertidlig løsning til alle notat er migrert over. Kan fjernes når alle notater er migrert
            .filter { tilType() != TypeBehandling.FORSKUDD || it.rolletype == Rolletype.BIDRAGSMOTTAKER }
            .map { rolle ->
                henteInntektsnotat(this, rolle.id!!)?.takeIfNotNullOrEmpty {
                    opprettGrunnlagNotat(Notattype.INNTEKT, false, it, rolle.tilGrunnlagsreferanse())
                }
            }.filterNotNull()

    return (notatGrunnlag + notatGrunnlagInntekter).toSet()
}

fun Behandling.tilSkyldner() =
    when (stonadstype) {
        Stønadstype.FORSKUDD -> skyldnerNav
        else ->
            bidragspliktig?.tilNyestePersonident()
                ?: rolleManglerIdent(Rolletype.BIDRAGSPLIKTIG, id!!)
    }

fun Behandling.tilBehandlingreferanseListe() =
    listOf(
        OpprettBehandlingsreferanseRequestDto(
            kilde = BehandlingsrefKilde.BEHANDLING_ID,
            referanse = id.toString(),
        ),
        OpprettBehandlingsreferanseRequestDto(
            kilde = BehandlingsrefKilde.BISYS_SØKNAD,
            referanse = soknadsid.toString(),
        ),
        soknadRefId?.let {
            OpprettBehandlingsreferanseRequestDto(
                kilde = BehandlingsrefKilde.BISYS_KLAGE_REF_SØKNAD,
                referanse = it.toString(),
            )
        },
    ).filterNotNull()
