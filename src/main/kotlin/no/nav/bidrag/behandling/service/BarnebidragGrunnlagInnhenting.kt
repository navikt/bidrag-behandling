package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.consumer.BidragBeløpshistorikkConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderBarn
import no.nav.bidrag.behandling.database.datamodell.hentSisteGrunnlagSomGjelderRolle
import no.nav.bidrag.behandling.database.datamodell.konvertereData
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.byggGrunnlagBeløpshistorikkBidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.byggGrunnlagBeløpshistorikkBidrag18År
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.byggGrunnlagBeløpshistorikkForskudd
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilGrunnlagBeløpshistorikk
import no.nav.bidrag.behandling.transformers.vedtak.personIdentNav
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
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

val søknadstyperSomKreverBeløpshistorikkForskudd = setOf(Behandlingstype.BEGRENSET_REVURDERING)
val søknadstyperSomKreverBeløpshistorikkBidrag = setOf(Behandlingstype.BEGRENSET_REVURDERING)
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
            val grunnlag = behandling.byggGrunnlagBeløpshistorikkForskudd(søknadsbarn)
            grunnlagsliste.add(grunnlag)
        }

        if (søknadsbarn.stønadstypeBarnEllerBehandling == Stønadstype.BIDRAG18AAR) {
            val grunnlag = behandling.byggGrunnlagBeløpshistorikkBidrag18År(søknadsbarn)
            grunnlagsliste.add(grunnlag)
        }

        val grunnlag = behandling.byggGrunnlagBeløpshistorikkBidrag(søknadsbarn)
        grunnlagsliste.add(grunnlag)
        return grunnlagsliste
    }

    fun hentBeløpshistorikk(
        behandling: Behandling,
        søknadsbarn: Rolle,
        stønadstype: Stønadstype,
        fraOpprinneligVedtakstidspunkt: Boolean = true,
    ): StønadDto? {
        val request =
            if (stønadstype == Stønadstype.FORSKUDD) {
                behandling.createStønadHistoriskRequest(
                    stønadstype = Stønadstype.FORSKUDD,
                    skyldner = personIdentNav,
                    søknadsbarn = søknadsbarn,
                    fraOpprinneligVedtakstidspunkt = fraOpprinneligVedtakstidspunkt,
                )
            } else {
                behandling.createStønadHistoriskRequest(
                    stønadstype = stønadstype,
                    søknadsbarn = søknadsbarn,
                    saksnummer = Saksnummer(søknadsbarn.saksnummer),
                    skyldner = Personident(behandling.bidragspliktig!!.ident!!),
                    fraOpprinneligVedtakstidspunkt = fraOpprinneligVedtakstidspunkt,
                )
            }
        return bidragBeløpshistorikkConsumer.hentHistoriskeStønader(request)
    }

    private fun Behandling.createStønadHistoriskRequest(
        stønadstype: Stønadstype,
        søknadsbarn: Rolle,
        skyldner: Personident?,
        saksnummer: Saksnummer? = null,
        fraOpprinneligVedtakstidspunkt: Boolean,
    ) = HentStønadHistoriskRequest(
        type = stønadstype,
        sak = saksnummer ?: Saksnummer(this.saksnummer),
        skyldner = skyldner ?: Personident(bidragspliktig!!.ident!!),
        kravhaver = Personident(søknadsbarn.ident!!),
        gyldigTidspunkt =
            if (erKlageEllerOmgjøring && fraOpprinneligVedtakstidspunkt) {
                omgjøringsdetaljer!!.minsteVedtakstidspunkt!!.minusMinutes(1)
            } else {
                LocalDateTime.now()
            },
    )
}
