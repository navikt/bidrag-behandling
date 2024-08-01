package no.nav.bidrag.behandling.transformers.vedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SærbidragskategoriGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftDirekteBetaltGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftspostGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettBehandlingsreferanseRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettGrunnlagRequestDto

val grunnlagsreferanse_delberegning_utgift = "delberegning_utgift"
val grunnlagsreferanse_utgiftsposter = "utgiftsposter"
val grunnlagsreferanse_utgift_direkte_betalt = "utgift_direkte_betalt"

fun GrunnlagDto.tilOpprettRequestDto() =
    OpprettGrunnlagRequestDto(
        referanse = referanse,
        type = type,
        innhold = innhold,
        grunnlagsreferanseListe = grunnlagsreferanseListe,
        gjelderReferanse = gjelderReferanse,
    )

private fun opprettGrunnlagNotat(
    notatType: NotatGrunnlag.NotatType,
    medIVedtak: Boolean,
    innhold: String,
) = GrunnlagDto(
    referanse = "notat_${notatType}_${if (medIVedtak) "med_i_vedtaket" else "kun_i_notat"}",
    type = Grunnlagstype.NOTAT,
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
                        opprinneligMottattDato = opprinneligMottattdato,
                        mottattDato = mottattdato,
                        søktFraDato = søktFomDato,
                        søktAv = soknadFra,
                    ),
                ),
        ),
    )

fun Behandling.byggGrunnlagSærbidragKategori() =
    setOf(
        GrunnlagDto(
            referanse = "særbidrag_kategori",
            type = Grunnlagstype.SÆRBIDRAG_KATEGORI,
            innhold =
                POJONode(
                    SærbidragskategoriGrunnlag(
                        kategori = særbidragKategori,
                        beskrivelse = kategoriBeskrivelse,
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

fun Behandling.byggGrunnlagUtgiftsposter() =
    setOf(
        GrunnlagDto(
            referanse = grunnlagsreferanse_utgiftsposter,
            type = Grunnlagstype.UTGIFTSPOSTER,
            innhold =
                POJONode(
                    utgift!!.utgiftsposter.map {
                        UtgiftspostGrunnlag(
                            dato = it.dato,
                            type = it.type,
                            kravbeløp = it.kravbeløp,
                            godkjentBeløp = it.godkjentBeløp,
                            begrunnelse = it.begrunnelse,
                            betaltAvBp = it.betaltAvBp,
                        )
                    },
                ),
        ),
    )

fun Behandling.byggGrunnlagUtgiftDirekteBetalt() =
    setOf(
        GrunnlagDto(
            referanse = grunnlagsreferanse_utgift_direkte_betalt,
            type = Grunnlagstype.UTGIFT_DIREKTE_BETALT,
            innhold =
                POJONode(
                    UtgiftDirekteBetaltGrunnlag(
                        beløpDirekteBetalt = utgift!!.beløpDirekteBetaltAvBp,
                    ),
                ),
        ),
    )

fun Behandling.byggGrunnlagNotater() =
    setOf(
        virkningstidspunktbegrunnelseKunINotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, false, it)
        },
        virkningstidspunktsbegrunnelseIVedtakOgNotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, true, it)
        },
        boforholdsbegrunnelseKunINotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.BOFORHOLD, false, it)
        },
        boforholdsbegrunnelseIVedtakOgNotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.BOFORHOLD, true, it)
        },
        inntektsbegrunnelseKunINotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.INNTEKT, false, it)
        },
        inntektsbegrunnelseIVedtakOgNotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.INNTEKT, true, it)
        },
        utgiftsbegrunnelseKunINotat?.takeIfNotNullOrEmpty {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.UTGIFTER, false, it)
        },
    ).filterNotNull()

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
