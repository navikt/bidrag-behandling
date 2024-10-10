package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.beregning.validerForSærbidrag
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.beregn.særbidrag.BeregnSærbidragApi
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
    private val mapper: VedtakGrunnlagMapper,
) {
    private val beregnApi = BeregnForskuddApi()
    private val beregnSærbidragApi = BeregnSærbidragApi()

    fun beregneForskudd(behandling: Behandling): List<ResultatForskuddsberegningBarn> {
        behandling.run {
            mapper.run {
                validering.run { validerForBeregning() }
                return if (avslag != null) {
                    søknadsbarn.map {
                        tilResultatAvslag(it)
                    }
                } else {
                    søknadsbarn.map { rolle ->
                        val beregnForskudd = byggGrunnlagForBeregning(behandling, rolle)

                        try {
                            ResultatForskuddsberegningBarn(
                                rolle.mapTilResultatBarn(),
                                beregnApi.beregn(beregnForskudd),
                            )
                        } catch (e: Exception) {
                            LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, e.message!!)
                        }
                    }
                }
            }
        }
    }

    fun beregneSærbidrag(behandling: Behandling): BeregnetSærbidragResultat {
        mapper.validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val søknasdbarn = behandling.søknadsbarn.first()
        return if (mapper.validering.run { behandling.erDirekteAvslagUtenBeregning() }) {
            behandling.tilResultatAvslagSærbidrag()
        } else {
            try {
                val grunnlagBeregning =
                    mapper.byggGrunnlagForBeregning(behandling, søknasdbarn)
                beregnSærbidragApi.beregn(grunnlagBeregning, behandling.opprinneligVedtakstype ?: behandling.vedtakstype).let { resultat ->
                    resultat.validerForSærbidrag()
                    resultat
                }
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
                                resultatkode = mapper.validering.run { tilSærbidragAvslagskode()!! },
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
