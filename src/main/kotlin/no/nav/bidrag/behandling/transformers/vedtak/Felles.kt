package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentAlleIkkeAktiv
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.BehandlingsreferanseDto
import no.nav.bidrag.transport.behandling.vedtak.response.EngangsbeløpDto
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakPeriodeDto
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.sak.RolleDto
import java.time.LocalDate
import java.time.LocalDateTime

val særbidragDirekteAvslagskoderSomKreverBeregning = listOf(Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS)
val særbidragDirekteAvslagskoderSomInneholderUtgifter =
    listOf(Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS, Resultatkode.ALLE_UTGIFTER_ER_FORELDET)
val grunnlagstyperSomIkkeTrengerÅBekreftes =
    listOf(Grunnlagsdatatype.ARBEIDSFORHOLD)

fun Set<Grunnlag>.hentAlleSomMåBekreftes() = hentAlleIkkeAktiv().filter { !grunnlagstyperSomIkkeTrengerÅBekreftes.contains(it.type) }

val personIdentNav = Personident("NAV")
val inntektsrapporteringSomKreverSøknadsbarn =
    listOf(
        Inntektsrapportering.KONTANTSTØTTE,
        Inntektsrapportering.BARNETILLEGG,
        Inntektsrapportering.BARNETILSYN,
    )

data class StønadsendringPeriode(
    val barn: Rolle,
    val perioder: List<OpprettPeriodeRequestDto>,
    val grunnlag: Set<GrunnlagDto>,
)

fun Collection<BaseGrunnlag>.hentPersonMedIdent(ident: String?) = hentAllePersoner().find { it.personIdent == ident }

fun Collection<GrunnlagDto>.hentPersonNyesteIdent(ident: String?) =
    filter { it.erPerson() }.find { it.personIdent == hentNyesteIdent(ident)?.verdi || it.personIdent == ident }

// TODO: Reel mottaker fra bidrag-sak?
fun Set<Rolle>.reelMottakerEllerBidragsmottaker(rolle: RolleDto) =
    rolle.reellMottaker
        ?.ident
        ?.verdi
        ?.let { Personident(it) }
        ?: find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }!!.let { hentNyesteIdent(it.ident)!! }

fun String?.nullIfEmpty() = if (this.isNullOrEmpty()) null else this

fun <T, R> T?.takeIfNotNullOrEmpty(block: (T) -> R): R? =
    if (this == null ||
        this is String &&
        this.trim().isEmpty() ||
        this is List<*> &&
        this.isEmpty()
    ) {
        null
    } else {
        block(this)
    }

fun Inntekt?.ifTaMed(block: (Inntekt) -> Unit) {
    if (this?.taMed == true) block(this)
}

fun <T> Boolean?.ifFalse(block: (Boolean) -> T?): T? = if (this == false) block(this) else null

fun Rolle.opprettPersonBarnBPBMReferanse(type: Grunnlagstype = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER) =
    opprettPersonBarnBPBMReferanse(type, fødselsdato, ident, navn)

fun opprettPersonBarnBPBMReferanse(
    type: Grunnlagstype = Grunnlagstype.PERSON_BARN_BIDRAGSMOTTAKER,
    fødselsdato: LocalDate,
    ident: String?,
    navn: String?,
) = type.tilPersonreferanse(
    fødselsdato.toCompactString(),
    if (ident.isNullOrEmpty()) (fødselsdato.toCompactString() + navn).hashCode() else (ident + fødselsdato.toCompactString()).hashCode(),
)

fun OpprettVedtakRequestDto.tilVedtakDto(): VedtakDto =
    VedtakDto(
        type = type,
        opprettetAv = opprettetAv ?: "",
        opprettetAvNavn = opprettetAv,
        kilde = kilde,
        kildeapplikasjon = "behandling",
        vedtakstidspunkt = vedtakstidspunkt,
        enhetsnummer = enhetsnummer,
        innkrevingUtsattTilDato = innkrevingUtsattTilDato,
        fastsattILand = fastsattILand,
        opprettetTidspunkt = LocalDateTime.now(),
        behandlingsreferanseListe =
            behandlingsreferanseListe.map {
                BehandlingsreferanseDto(
                    kilde = it.kilde,
                    referanse = it.referanse,
                )
            },
        stønadsendringListe =
            stønadsendringListe.map {
                StønadsendringDto(
                    innkreving = it.innkreving,
                    skyldner = it.skyldner,
                    kravhaver = it.kravhaver,
                    mottaker = it.mottaker,
                    sak = it.sak,
                    type = it.type,
                    beslutning = it.beslutning,
                    grunnlagReferanseListe = it.grunnlagReferanseListe,
                    eksternReferanse = it.eksternReferanse,
                    omgjørVedtakId = it.omgjørVedtakId,
                    førsteIndeksreguleringsår = it.førsteIndeksreguleringsår,
                    sisteVedtaksid = null,
                    periodeListe =
                        it.periodeListe.map {
                            VedtakPeriodeDto(
                                periode = it.periode,
                                beløp = it.beløp,
                                valutakode = it.valutakode,
                                resultatkode = it.resultatkode,
                                delytelseId = it.delytelseId,
                                grunnlagReferanseListe = it.grunnlagReferanseListe,
                            )
                        },
                )
            },
        engangsbeløpListe =
            engangsbeløpListe.map {
                EngangsbeløpDto(
                    beløp = it.beløp,
                    valutakode = it.valutakode,
                    resultatkode = it.resultatkode,
                    delytelseId = it.delytelseId,
                    grunnlagReferanseListe = it.grunnlagReferanseListe,
                    beslutning = it.beslutning,
                    innkreving = it.innkreving,
                    skyldner = it.skyldner,
                    kravhaver = it.kravhaver,
                    mottaker = it.mottaker,
                    sak = it.sak,
                    type = it.type,
                    eksternReferanse = it.eksternReferanse,
                    omgjørVedtakId = it.omgjørVedtakId,
                    referanse = it.referanse ?: "",
                )
            },
        unikReferanse = null,
        vedtaksid = 1,
        grunnlagListe =
            grunnlagListe.map {
                GrunnlagDto(
                    referanse = it.referanse,
                    type = it.type,
                    innhold = it.innhold,
                    grunnlagsreferanseListe = it.grunnlagsreferanseListe,
                    gjelderReferanse = it.gjelderReferanse,
                    gjelderBarnReferanse = it.gjelderBarnReferanse,
                )
            },
    )
