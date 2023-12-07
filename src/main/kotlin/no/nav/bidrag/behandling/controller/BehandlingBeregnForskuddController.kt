package no.nav.bidrag.behandling.controller

import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.beregning.ForskuddBeregning
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.dto.beregning.ForskuddBeregningPerBarn
import no.nav.bidrag.behandling.dto.beregning.ForskuddBeregningRespons
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestController
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
    private val forskuddBeregning: ForskuddBeregning,
    private val bidragPersonConsumer: BidragPersonConsumer,
) {
    private fun isPeriodOneWithinPeriodTwo(
        datoFom1: LocalDate?,
        datoTom1: LocalDate?,
        datoFom2: LocalDate,
        datoTom2: LocalDate?,
    ) = (datoFom1 === null || datoFom1 > datoFom2 || datoFom1.isEqual(datoFom2)) && (
            (datoTom2 == null && datoTom1 == null) ||
                    (
                            datoTom1 != null && (
                                    datoTom2 == null || datoTom1 < datoTom2 ||
                                            datoTom1.isEqual(
                                                datoTom2,
                                            )
                                    )
                            )
            )

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(
        @PathVariable behandlingId: Long,
    ): ForskuddBeregningRespons {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val result =
            either {
                val behandlingModel =
                    forskuddBeregning.toBehandlingBeregningModel(behandling).bind()
                val results =
                    behandling
                        .getSøknadsBarn()
                        .mapOrAccumulate {
                            val fødselsdatoSøknadsbarn =
                                if (it.fodtDato == null && it.ident != null) {
                                    bidragPersonConsumer.hentPerson(it.ident).fødselsdato
                                } else {
                                    it.fodtDato?.toLocalDate()
                                }

                            // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            if (fødselsdatoSøknadsbarn == null) {
                                fantIkkeFødselsdatoTilSøknadsbarn(behandlingId)
                            }

                            var søknadsbarn =
                                forskuddBeregning.lagePersonobjektForSøknadsbarn(
                                    it,
                                    fødselsdatoSøknadsbarn,
                                )

                            val payload = forskuddBeregning.toPayload(behandlingModel, søknadsbarn)

                            try {
                                val respons =
                                    bidragBeregnForskuddConsumer.beregnForskudd(payload)
                                val beregnetForskuddPeriodeListe =
                                    respons.beregnetForskuddPeriodeListe
                                beregnetForskuddPeriodeListe.forEach { r ->
                                    r.sivilstand =
                                        behandlingModel.sivilstand.find { sivilstand ->
                                            isPeriodOneWithinPeriodTwo(
                                                r.periode.datoFom,
                                                r.periode.datoTil,
                                                sivilstand.datoFom,
                                                sivilstand.datoTom,
                                            )
                                        }?.sivilstand
                                }
                                ForskuddBeregningPerBarn(
                                    referanseTilBarn = søknadsbarn.referanse,
                                    beregnetForskuddPeriodeListe = beregnetForskuddPeriodeListe,
                                    grunnlagListe = respons.grunnlagListe,
                                )
                            } catch (e: HttpClientErrorException) {
                                LOGGER.warn { e }
                                val errors = e.responseHeaders?.get("error")?.joinToString("\r\n")
                                raise(errors ?: e.message!!)
                            } catch (e: Exception) {
                                LOGGER.warn { e }
                                raise(e.message!!)
                            }
                        }.bind()

                return@either results
            }

        return ForskuddBeregningRespons(result.getOrNull(), result.leftOrNull())
    }
}
