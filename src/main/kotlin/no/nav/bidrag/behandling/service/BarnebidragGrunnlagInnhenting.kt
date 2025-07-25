package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.belopshistorikk.request.HentStønadHistoriskRequest
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.BeløpshistorikkPeriode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.contains

val søknadstyperSomKreverBeløpshistorikkForskudd = setOf(BisysSøknadstype.BEGRENSET_REVURDERING)
val søknadstyperSomKreverBeløpshistorikkBidrag = setOf(BisysSøknadstype.BEGRENSET_REVURDERING)
val stønadstyperSomKreverBeløpshistorikkBidrag = setOf(Stønadstype.BIDRAG, Stønadstype.BIDRAG18AAR)

@Service
class BarnebidragGrunnlagInnhenting(
    private val bidragBeløpshistorikkConsumer: BidragBeløpshistorikkConsumer,
) {
    fun byggGrunnlagBeløpshistorikk(
        behandling: Behandling,
        søknadsbarn: Rolle,
    ): Set<GrunnlagDto> {
        val grunnlagsliste = mutableSetOf<GrunnlagDto>()
        if (behandling.stonadstype == null || !stønadstyperSomKreverBeløpshistorikkBidrag.contains(behandling.stonadstype)) {
            return emptySet()
        }
        if (søknadstyperSomKreverBeløpshistorikkForskudd.contains(behandling.søknadstype)) {
            val grunnlag =
                behandling.grunnlag
                    .hentSisteGrunnlagSomGjelderBarn(søknadsbarn.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_FORSKUDD)
                    .konvertereData<StønadDto>()
                    .tilGrunnlag(
                        kravhaver = søknadsbarn.ident!!,
                        skyldner = personIdentNav.verdi,
                        type = Stønadstype.FORSKUDD,
                        behandling,
                        søknadsbarn,
                    )
            grunnlagsliste.add(grunnlag)
        }

        if (behandling.stonadstype == Stønadstype.BIDRAG18AAR) {
            val grunnlag =
                behandling.grunnlag
                    .hentSisteGrunnlagSomGjelderBarn(søknadsbarn.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG_18_ÅR)
                    .konvertereData<StønadDto>()
                    .tilGrunnlag(
                        kravhaver = søknadsbarn.ident!!,
                        skyldner = behandling.bidragspliktig!!.ident!!,
                        type = Stønadstype.BIDRAG18AAR,
                        behandling,
                        søknadsbarn,
                    )
            grunnlagsliste.add(grunnlag)
        }

        val grunnlag =
            behandling.grunnlag
                .hentSisteGrunnlagSomGjelderBarn(søknadsbarn.ident!!, Grunnlagsdatatype.BELØPSHISTORIKK_BIDRAG)
                .konvertereData<StønadDto>()
                .tilGrunnlag(
                    kravhaver = søknadsbarn.ident!!,
                    skyldner = behandling.bidragspliktig!!.ident!!,
                    type = Stønadstype.BIDRAG,
                    behandling,
                    søknadsbarn,
                )
        grunnlagsliste.add(grunnlag)
        return grunnlagsliste
    }

    fun hentBeløpshistorikk(
        behandling: Behandling,
        søknadsbarn: Rolle,
        stønadstype: Stønadstype,
    ): StønadDto? {
        val request =
            if (stønadstype == Stønadstype.FORSKUDD) {
                behandling.createStønadHistoriskRequest(
                    stønadstype = Stønadstype.FORSKUDD,
                    skyldner = personIdentNav,
                    søknadsbarn = søknadsbarn,
                )
            } else {
                behandling.createStønadHistoriskRequest(
                    stønadstype = stønadstype,
                    søknadsbarn = søknadsbarn,
                    skyldner = Personident(behandling.bidragspliktig!!.ident!!),
                )
            }
        return bidragBeløpshistorikkConsumer.hentHistoriskeStønader(request)
    }

    fun StønadDto?.tilGrunnlag(
        kravhaver: String,
        skyldner: String,
        type: Stønadstype,
        behandling: Behandling,
        søknadsbarn: Rolle,
    ): GrunnlagDto {
        val grunnlagstype =
            when (type) {
                Stønadstype.BIDRAG -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG
                Stønadstype.BIDRAG18AAR -> Grunnlagstype.BELØPSHISTORIKK_BIDRAG_18_ÅR
                Stønadstype.FORSKUDD -> Grunnlagstype.BELØPSHISTORIKK_FORSKUDD
                else -> throw IllegalArgumentException("Ukjent stønadstype")
            }

        return GrunnlagDto(
            referanse =
                "${grunnlagstype}_${behandling.saksnummer}_${kravhaver}_$skyldner" +
                    "_${LocalDate.now().toCompactString()}",
            type = grunnlagstype,
            gjelderReferanse =
                when {
                    type == Stønadstype.BIDRAG -> behandling.bidragspliktig!!.tilGrunnlagsreferanse()
                    type == Stønadstype.BIDRAG18AAR -> behandling.bidragspliktig!!.tilGrunnlagsreferanse()
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
                        nesteIndeksreguleringsår = this?.nesteIndeksreguleringsår ?: this?.førsteIndeksreguleringsår,
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
