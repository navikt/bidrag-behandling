package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.skyldnerNav
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.stonad.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.stonad.response.StønadDto
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.contains

val søknadstyperSomKreverBeløpshistorikkForskudd = setOf(BisysSøknadstype.BEGRENSET_REVURDERING)
val søknadstyperSomKreverBeløpshistorikkBidrag = setOf(BisysSøknadstype.BEGRENSET_REVURDERING)

@Service
class BarnebidragGrunnlagInnhenting(
    private val bidragStønadConsumer: BidragStønadConsumer,
) {
    fun byggGrunnlagBeløpshistorikk(
        behandling: Behandling,
        søknadsbarn: Rolle,
    ): Set<GrunnlagDto> {
        val grunnlagsliste = mutableSetOf<GrunnlagDto>()
        if (behandling.stonadstype != Stønadstype.BIDRAG || behandling.stonadstype == null) {
            return emptySet()
        }
        if (søknadstyperSomKreverBeløpshistorikkForskudd.contains(behandling.søknadstype)) {
            val request =
                behandling.createStønadHistoriskRequest(
                    stønadstype = Stønadstype.FORSKUDD,
                    skyldner = skyldnerNav,
                    søknadsbarn = søknadsbarn,
                )
            val grunnlag =
                bidragStønadConsumer
                    .hentHistoriskeStønader(request)
                    .tilGrunnlag(request, behandling, søknadsbarn)
            grunnlagsliste.add(grunnlag)
        }

        if (søknadstyperSomKreverBeløpshistorikkBidrag.contains(behandling.søknadstype)) {
            val request =
                behandling.createStønadHistoriskRequest(
                    stønadstype = Stønadstype.BIDRAG,
                    søknadsbarn = søknadsbarn,
                    skyldner = Personident(behandling.bidragspliktig!!.ident!!),
                )
            val grunnlag = bidragStønadConsumer.hentHistoriskeStønader(request).tilGrunnlag(request, behandling, søknadsbarn)
            grunnlagsliste.add(grunnlag)
        }
        return grunnlagsliste
    }

    fun hentBeløpshistorikkBidrag(
        behandling: Behandling,
        søknadsbarn: Rolle,
    ): StønadDto? {
        val request =
            behandling.createStønadHistoriskRequest(
                stønadstype = Stønadstype.BIDRAG,
                søknadsbarn = søknadsbarn,
                skyldner = Personident(behandling.bidragspliktig!!.ident!!),
            )
        return bidragStønadConsumer.hentHistoriskeStønader(request)
    }

    fun StønadDto?.tilGrunnlag(
        request: HentStønadHistoriskRequest,
        behandling: Behandling,
        søknadsbarn: Rolle,
    ): GrunnlagDto {
        val grunnlagstype =
            when (request.type) {
                Stønadstype.BIDRAG -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG
                Stønadstype.FORSKUDD -> Grunnlagstype.BELØPSHISTORIKK_FORSKUDD
                else -> throw IllegalArgumentException("Ukjent stønadstype")
            }

        return GrunnlagDto(
            referanse =
                "${grunnlagstype}_${request.sak.verdi}_${request.kravhaver.verdi}_${request.skyldner.verdi}" +
                    "_${LocalDate.now().toCompactString()}",
            type = grunnlagstype,
            gjelderReferanse =
                when {
                    request.type == Stønadstype.BIDRAG -> behandling.bidragspliktig!!.tilGrunnlagsreferanse()
                    this != null && this.mottaker.verdi != behandling.bidragsmottaker!!.ident -> {
                        // TODO: What to do here?
                        behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                    }
                    else -> behandling.bidragsmottaker!!.tilGrunnlagsreferanse()
                },
            gjelderBarnReferanse = søknadsbarn.tilGrunnlagsreferanse(),
            innhold =
                POJONode(
                    BeløpshistorikkGrunnlag(
                        tidspunktInnhentet = LocalDateTime.now(),
                        førsteIndeksreguleringsår = this?.førsteIndeksreguleringsår,
                        beløpshistorikk =
                            this?.periodeListe?.map {
                                BeløpshistorikkPeriode(
                                    periode = it.periode,
                                    beløp = it.beløp,
                                    valutakode = it.valutakode,
                                    vedtaksid = it.vedtaksid,
                                )
                            } ?: emptyList(),
                    ),
                ),
        )
    }

    private fun Behandling.createStønadHistoriskRequest(
        stønadstype: Stønadstype,
        søknadsbarn: Rolle,
        skyldner: Personident?,
    ) = HentStønadHistoriskRequest(
        type = stønadstype,
        sak = Saksnummer(saksnummer),
        skyldner = skyldner ?: Personident(bidragspliktig!!.ident!!),
        kravhaver = Personident(søknadsbarn.ident!!),
        gyldigTidspunkt = LocalDateTime.now(),
    )
}
