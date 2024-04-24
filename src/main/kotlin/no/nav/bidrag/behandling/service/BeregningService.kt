package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.validerForBeregning
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForBeregning
import no.nav.bidrag.behandling.transformers.nærmesteHeltall
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() = ResultatRolle(tilPersonident(), hentNavn(), foedselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
) {
    private val beregnApi = BeregnForskuddApi()

    fun beregneForskudd(behandlingsid: Long): List<ResultatForskuddsberegningBarn> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        behandling.validerForBeregning()
        return if (behandling.avslag != null) {
            behandling.søknadsbarn.map {
                behandling.tilResultatAvslag(it)
            }
        } else {
            behandling.søknadsbarn.map {
                val beregnForskudd = behandling.byggGrunnlagForBeregning(it)

                try {
                    val resultat = beregnApi.beregn(beregnForskudd)
                    ResultatForskuddsberegningBarn(
                        it.mapTilResultatBarn(),
                        resultat.copy(
                            beregnetForskuddPeriodeListe =
                                resultat.beregnetForskuddPeriodeListe.map { resultatPeriode ->
                                    resultatPeriode.copy(
                                        resultat =
                                            resultatPeriode.resultat.copy(
                                                belop = resultatPeriode.resultat.belop.nærmesteHeltall,
                                            ),
                                    )
                                },
                        ),
                    )
                } catch (e: Exception) {
                    LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                    throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                }
            }
        }
    }

    private fun Behandling.tilResultatAvslag(barn: Rolle) =
        ResultatForskuddsberegningBarn(
            barn.mapTilResultatBarn(),
            BeregnetForskuddResultat(
                beregnetForskuddPeriodeListe =
                    listOf(
                        ResultatPeriode(
                            periode = ÅrMånedsperiode(virkningstidspunkt!!, null),
                            grunnlagsreferanseListe = emptyList(),
                            resultat =
                                ResultatBeregning(
                                    belop = BigDecimal.ZERO,
                                    kode = avslag!!,
                                    regel = "",
                                ),
                        ),
                    ),
            ),
        )
}
