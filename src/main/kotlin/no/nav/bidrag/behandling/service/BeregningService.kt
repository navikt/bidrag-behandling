package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.validerForBeregning
import no.nav.bidrag.behandling.database.datamodell.validerForBeregningSærbidrag
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.grunnlag.byggGrunnlagForBeregning
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.forskudd.BeregnetForskuddResultat
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatBeregning
import no.nav.bidrag.transport.behandling.beregning.forskudd.ResultatPeriode
import no.nav.bidrag.transport.behandling.beregning.særbidrag.BeregnetSærbidragResultat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatBeregning as ResultatBeregningSærbidrag
import no.nav.bidrag.transport.behandling.beregning.særbidrag.ResultatPeriode as ResultatPeriodeSærbidrag

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() = ResultatRolle(tilPersonident(), hentNavn(), fødselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
) {
    private val beregnApi = BeregnForskuddApi()

    fun beregneForskudd(behandling: Behandling): List<ResultatForskuddsberegningBarn> {
        behandling.validerForBeregning()
        return if (behandling.avslag != null) {
            behandling.søknadsbarn.map {
                behandling.tilResultatAvslag(it)
            }
        } else {
            behandling.søknadsbarn.map {
                val beregnForskudd = behandling.byggGrunnlagForBeregning(it)

                try {
                    ResultatForskuddsberegningBarn(
                        it.mapTilResultatBarn(),
                        beregnApi.beregn(beregnForskudd),
                    )
                } catch (e: Exception) {
                    LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                    throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                }
            }
        }
    }

    fun beregneSærbidrag(behandling: Behandling): BeregnetSærbidragResultat {
        behandling.validerForBeregningSærbidrag()
        val søknasdbarn = behandling.søknadsbarn.first()
        return if (behandling.avslag != null) {
            behandling.tilResultatAvslagSærbidrag()
        } else {
            val grunnlagBeregning = behandling.byggGrunnlagForBeregning(søknasdbarn)
            secureLogger.info { "Grunnlag særbidrag $grunnlagBeregning" }
            try {
                // TODO: Legg til særbidrag beregning
                BeregnetSærbidragResultat(
                    beregnetSærbidragPeriodeListe =
                        listOf(
                            ResultatPeriodeSærbidrag(
                                grunnlagsreferanseListe = emptyList(),
                                periode =
                                    ÅrMånedsperiode(
                                        behandling.virkningstidspunkt!!,
                                        behandling.virkningstidspunkt!!.plusMonths(1),
                                    ),
                                resultat =
                                    ResultatBeregningSærbidrag(
                                        beløp = BigDecimal.ZERO,
                                        resultatkode = Resultatkode.SÆRBIDRAG_INNVILGET,
                                    ),
                            ),
                        ),
                    grunnlagListe = grunnlagBeregning.grunnlagListe,
                )
            } catch (e: Exception) {
                LOGGER.warn(e) { "Det skjedde en feil ved beregning av særbidrag: ${e.message}" }
                throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
            }
        }
    }

    fun beregneForskudd(behandlingsid: Long): List<ResultatForskuddsberegningBarn> {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneForskudd(behandling)
    }

    fun beregneSærbidrag(behandlingsid: Long): BeregnetSærbidragResultat {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        return beregneSærbidrag(behandling)
    }

    private fun Behandling.tilResultatAvslagSærbidrag() =
        BeregnetSærbidragResultat(
            beregnetSærbidragPeriodeListe =
                listOf(
                    ResultatPeriodeSærbidrag(
                        grunnlagsreferanseListe = emptyList(),
                        periode = ÅrMånedsperiode(virkningstidspunkt!!, virkningstidspunkt!!.plusMonths(1)),
                        resultat =
                            ResultatBeregningSærbidrag(
                                beløp = BigDecimal.ZERO,
                                resultatkode = avslag!!,
                            ),
                    ),
                ),
            grunnlagListe = emptyList(),
        )

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
