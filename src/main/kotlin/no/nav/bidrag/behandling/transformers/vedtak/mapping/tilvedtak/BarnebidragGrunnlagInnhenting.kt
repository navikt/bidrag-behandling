package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
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
        val requestTemp =
            HentStønadHistoriskRequest(
                type = Stønadstype.FORSKUDD,
                sak = Saksnummer(behandling.saksnummer),
                skyldner = Personident(behandling.bidragspliktig!!.ident!!),
                kravhaver = Personident(søknadsbarn.ident!!),
                gyldigTidspunkt = LocalDateTime.now(),
            )
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
                    .tilGrunnlag(request)
            grunnlagsliste.add(grunnlag)
        }

        if (søknadstyperSomKreverBeløpshistorikkBidrag.contains(behandling.søknadstype)) {
            val request = requestTemp.copy(type = Stønadstype.BIDRAG)
            val grunnlag = bidragStønadConsumer.hentHistoriskeStønader(request).tilGrunnlag(request)
            grunnlagsliste.add(grunnlag)
        }
        return grunnlagsliste
    }

    fun StønadDto?.tilGrunnlag(request: HentStønadHistoriskRequest): GrunnlagDto {
        val grunnlagstype =
            when (request.type) {
                Stønadstype.BIDRAG -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG
                Stønadstype.FORSKUDD -> Grunnlagstype.BELØPSHISTORIKK_FORSKUDD
                else -> throw IllegalArgumentException("Ukjent stønadstype")
            }

        return GrunnlagDto(
            referanse =
                "${grunnlagstype}_${request.sak.verdi}_${request.kravhaver.verdi}_${request.skyldner.verdi}" +
                    "_${LocalDateTime.now().toCompactString()}",
            type = grunnlagstype,
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
