package no.nav.bidrag.behandling.transformers.vedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BehandlingGrunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.tilPersonident
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.manglerRolle
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.hentGrunnlagSomInneholderPeriode
import no.nav.bidrag.behandling.transformers.oppretteGrunnlagForHusstandsbarn
import no.nav.bidrag.behandling.transformers.personIdent
import no.nav.bidrag.behandling.transformers.tilGrunnlagBostatus
import no.nav.bidrag.behandling.transformers.tilGrunnlagInntekt
import no.nav.bidrag.behandling.transformers.tilGrunnlagSivilstand
import no.nav.bidrag.behandling.transformers.tilPersonGrunnlag
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
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto

fun GrunnlagDto.tilOpprettRequestDto() =
    OpprettGrunnlagRequestDto(
        referanse = referanse,
        type = type,
        innhold = innhold,
        grunnlagsreferanseListe = grunnlagsreferanseListe,
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
                        mottattDato = mottattdato,
                        søktFraDato = søktFomDato,
                        søktAv = soknadFra,
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
                        årsak = årsak!!,
                    ),
                ),
        ),
    )

fun Behandling.byggGrunnlagNotater() =
    setOf(
        virkningstidspunktbegrunnelseKunINotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, false, it)
        },
        virkningstidspunktsbegrunnelseIVedtakOgNotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, true, it)
        },
        boforholdsbegrunnelseKunINotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.BOFORHOLD, false, it)
        },
        boforholdsbegrunnelseIVedtakOgNotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.BOFORHOLD, true, it)
        },
        inntektsbegrunnelseKunINotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.INNTEKT, false, it)
        },
        inntektsbegrunnelseIVedtakOgNotat?.let {
            opprettGrunnlagNotat(NotatGrunnlag.NotatType.INNTEKT, true, it)
        },
    ).filterNotNull()

fun Behandling.byggGrunnlagBarn(
    søknadsbarnRolle: Rolle,
    grunnlag: List<BehandlingGrunnlag>,
): Set<GrunnlagDto> {
    val bm = bidragsmottaker?.tilPersonGrunnlag() ?: manglerRolle(Rolletype.BIDRAGSMOTTAKER, id!!)
    val søknadsbarn = søknadsbarnRolle.tilPersonGrunnlag()
    val øvrigeBarnIHusstand = oppretteGrunnlagForHusstandsbarn(søknadsbarn.personIdent)
    val personobjekter = listOf(søknadsbarn) + øvrigeBarnIHusstand

    val bostatusBarn =
        tilGrunnlagBostatus(personobjekter.toSet())
    val innhentetHusstandsmedlemmerBm = grunnlag.tilInnhentetHusstandsmedlemmer(bm, personobjekter)

    val sivilstandBm =
        tilGrunnlagSivilstand(bm)
    val innhentetSivilstandBm = grunnlag.tilInnhentetSivilstand(bm)

    val innhentetArbeidsforholdBm = grunnlag.tilInnhentetArbeidsforhold(bm)
    val innhentetArbeidsforholdBa = grunnlag.tilInnhentetArbeidsforhold(søknadsbarn)

    val inntekterBm =
        tilGrunnlagInntekt(bm, søknadsbarn) +
            grunnlag.tilInnhentetGrunnlagInntekt(
                bm,
                søknadsbarn,
            ) + grunnlag.tilBeregnetInntekt(bm)

    val inntekterBa =
        tilGrunnlagInntekt(søknadsbarn) + grunnlag.tilInnhentetGrunnlagInntektBarn(søknadsbarn) +
            grunnlag.tilBeregnetInntekt(
                søknadsbarn,
            )

    val grunnlagBp =
        if (stonadstype != Stønadstype.FORSKUDD) {
            val bp =
                bidragspliktig?.tilPersonGrunnlag() ?: manglerRolle(Rolletype.BIDRAGSPLIKTIG, id!!)
            val innhentetInntekterBp =
                grunnlag.tilInnhentetGrunnlagInntekt(bp, søknadsbarn) +
                    grunnlag.tilBeregnetInntekt(
                        bp,
                    )
            val inntekterLagtTilGrunnBp = tilGrunnlagInntekt(bp, søknadsbarn)
            val sivilstandBp = tilGrunnlagSivilstand(bp)
            val innhentetSivilstandBp = grunnlag.tilInnhentetSivilstand(bp)
            val innhentetHusstandsmedlemBp =
                grunnlag.tilInnhentetHusstandsmedlemmer(bp, personobjekter)
            val innhentetArbeidsforholdBp = grunnlag.tilInnhentetArbeidsforhold(bp)
            inntekterLagtTilGrunnBp + innhentetInntekterBp +
                innhentetHusstandsmedlemBp + innhentetSivilstandBp + sivilstandBp + innhentetArbeidsforholdBp
        } else {
            emptyList()
        }

    return bostatusBarn + sivilstandBm + inntekterBm + personobjekter +
        grunnlagBp + innhentetArbeidsforholdBm + inntekterBa +
        innhentetHusstandsmedlemmerBm + innhentetSivilstandBm + innhentetArbeidsforholdBa + bm
}

data class StønadsendringPeriode(
    val barn: Rolle,
    val perioder: List<OpprettPeriodeRequestDto>,
    val grunnlag: Set<GrunnlagDto>,
)

fun ResultatForskuddsberegningBarn.byggStønadsendringerForVedtak(
    behandling: Behandling,
    grunnlag: List<BehandlingGrunnlag>,
): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == this.barn.ident?.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)
    val grunnlagListe =
        behandling.byggGrunnlagBarn(
            søknadsbarn,
            grunnlag,
        ) + resultat.grunnlagListe.filter { it.type == Grunnlagstype.SJABLON }
    val periodeliste =
        resultat.beregnetForskuddPeriodeListe.map {
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp = it.resultat.belop,
                valutakode = "NOK",
                resultatkode = it.resultat.kode.name,
                grunnlagReferanseListe =
                    grunnlagListe.hentGrunnlagSomInneholderPeriode(it.periode)
                        .map { grunnlag -> grunnlag.referanse },
            )
        }

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste,
        grunnlagListe,
    )
}

fun Behandling.tilSkyldner() =
    when (stonadstype) {
        Stønadstype.FORSKUDD -> skyldnerNav
        else ->
            bidragspliktig?.tilPersonident()
                ?: rolleManglerIdent(Rolletype.BIDRAGSPLIKTIG, id!!)
    }

fun Behandling.tilBehandlingreferanseList() =
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
