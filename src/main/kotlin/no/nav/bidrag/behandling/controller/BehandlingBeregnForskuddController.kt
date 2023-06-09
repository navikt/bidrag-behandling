package no.nav.bidrag.behandling.controller

import com.fasterxml.jackson.databind.node.POJONode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.BidragPersonConsumer
import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.behandling.dto.behandling.Periode
import no.nav.bidrag.behandling.dto.behandling.ResultatBeregning
import no.nav.bidrag.behandling.dto.behandling.ResultatPeriode
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.math.BigDecimal
import java.time.LocalDate

@BehandlingRestController
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val bidragPersonConsumer: BidragPersonConsumer,
) {

    @Suppress("unused")
    @PostMapping("/behandling/{behandlingId}/beregn")
    @Operation(
        description = "Beregn forskudd",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun beregnForskudd(@PathVariable behandlingId: Long): ForskuddDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        try {
            val beregnForskudd = bidragPersonConsumer.beregnForskudd(preparePayload(behandling))
        } catch (e: Exception) {
            //
        }

        // må returneres `beregnForskudd`
        return ForskuddDto(
            beregnetForskuddPeriodeListe = listOf(
                ResultatPeriode(
                    periode = Periode(),
                    resultat = ResultatBeregning(BigDecimal.valueOf(1000.10)),
                ),
            ),
        )
    }

    fun prepareSøknadsBarn(behandling: Behandling): List<Grunnlag> {
        // TODO
        return emptyList()
    }

    fun prepareBostatus(behandling: Behandling): List<Grunnlag> {
        // TODO
        return emptyList()
    }

    fun prepareInntekterForBeregning(behandling: Behandling): List<Grunnlag> =
        behandling.inntekter.map {
            Grunnlag(
                referanse = "",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoTom.toLocalDate(),
                        datoTil = it.datoTom.toLocalDate(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "INNTEKTSOPPLYSNINGER_ARBEIDSGIVER", // TODO vi kanskje trenger flere typer her
                        belop = it.beløp,
                    ),
                ),
            )
        } + behandling.barnetillegg.map {
            Grunnlag(
                referanse = "",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoTom.toLocalDate(),
                        datoTil = it.datoTom.toLocalDate(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "EKSTRA_SMAABARNSTILLEGG",
                        belop = it.barnetillegg,
                    ),
                ),
            )
        } + behandling.utvidetbarnetrygd.map {
            Grunnlag(
                referanse = "",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoTom.toLocalDate(),
                        datoTil = it.datoTom.toLocalDate(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "UTVIDET_BARNETRYGD",
                        belop = it.beløp,
                    ),
                ),
            )
        }

    fun prepareSivilstand(behandling: Behandling): List<Grunnlag> =
        behandling.sivilstand.map {
            Grunnlag(
                referanse = "",
                type = "SIVILSTAND",
                innhold = POJONode(
                    SivilstandNode(
                        datoFom = it.gyldigFraOgMed.toLocalDate(),
                        datoTil = it.datoTom?.toLocalDate(),
                        rolle = "BIDRAGSMOTTAKER",
                        sivilstandKode = it.sivilstandType.name,
                    ),
                ),
            )
        }

    fun preparePayload(behandling: Behandling): BeregnForskuddPayload {
        return BeregnForskuddPayload(
            beregnDatoFra = behandling.virkningsDato?.toLocalDate(), // TODO kanskje behandling.datoFom?
            beregnDatoTil = behandling.datoTom.toLocalDate(),
            grunnlagListe = prepareSøknadsBarn(behandling) +
                prepareBostatus(behandling) +
                prepareInntekterForBeregning(behandling) +
                prepareSivilstand(behandling),

        )
    }
}

data class InntektNode(
    val datoFom: LocalDate?,
    val datoTil: LocalDate?,
    val rolle: String?,
    val inntektType: String?,
    val belop: BigDecimal?,
)

data class SivilstandNode(
    val datoFom: LocalDate?,
    val datoTil: LocalDate?,
    val rolle: String?,
    val sivilstandKode: String?,
)
