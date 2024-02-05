package no.nav.bidrag.behandling.service

import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.hentNavn
import no.nav.bidrag.behandling.database.datamodell.validere
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegning
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatRolle
import no.nav.bidrag.behandling.transformers.tilBeregnGrunnlagBarn
import no.nav.bidrag.behandling.valideringAvBehandlingFeilet
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.domene.ident.Personident
import org.springframework.stereotype.Service

private val LOGGER = KotlinLogging.logger {}

private fun Rolle.tilPersonident() = ident?.let { Personident(it) }

private fun Rolle.mapTilResultatBarn() = ResultatRolle(tilPersonident(), hentNavn(), foedselsdato)

@Service
class BeregningService(
    private val behandlingService: BehandlingService,
    private val beregnForskuddApi: BeregnForskuddApi,
) {
    fun beregneForskudd(behandlingsid: Long): ResultatForskuddsberegning {
        val respons =
            either {
                val behandling =
                    behandlingService.hentBehandlingById(behandlingsid).validere().bind()
                val resultat =
                    behandling.søknadsbarn.mapOrAccumulate {
                        val beregnForskudd =
                            behandling.tilBeregnGrunnlagBarn(it)

                        try {
                            ResultatForskuddsberegningBarn(
                                it.mapTilResultatBarn(),
                                beregnForskuddApi.beregn(beregnForskudd),
                            )
                        } catch (e: IllegalArgumentException) {
                            LOGGER.warn(e) { "Det skjedde en feil ved beregning av forskudd: ${e.message}" }
                            raise(e.message!!)
                        } catch (e: Exception) {
                            LOGGER.warn { e }
                            raise(e.message!!)
                        }
                    }.bind()

                return@either resultat
            }

        if (respons.isLeft()) {
            respons.leftOrNull()?.let {
                LOGGER.warn { "Validering av forskuddsbehandling feilet med: ${it.all}" }
                valideringAvBehandlingFeilet(it.all)
            }
        }

        return ResultatForskuddsberegning(respons.getOrNull() ?: emptyList())
    }
}
