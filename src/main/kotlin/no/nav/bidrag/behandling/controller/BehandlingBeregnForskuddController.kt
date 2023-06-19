package no.nav.bidrag.behandling.controller

import com.fasterxml.jackson.databind.node.POJONode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
import no.nav.bidrag.behandling.dto.behandling.Periode
import no.nav.bidrag.behandling.dto.behandling.ResultatBeregning
import no.nav.bidrag.behandling.dto.behandling.ResultatPeriode
import no.nav.bidrag.behandling.service.BehandlingService
import no.nav.bidrag.behandling.transformers.toLocalDate
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.math.BigDecimal

private val LOGGER = KotlinLogging.logger {}

@BehandlingRestController
class BehandlingBeregnForskuddController(
    private val behandlingService: BehandlingService,
    private val bidragBeregnForskuddConsumer: BidragBeregnForskuddConsumer,
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
            val beregnForskudd = bidragBeregnForskuddConsumer.beregnForskudd(preparePayload(behandling))
            return beregnForskudd
        } catch (e: Exception) {
            LOGGER.warn { e }
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

    fun prepareSoknadsBarn(behandling: Behandling): List<Grunnlag> =
        behandling
            .roller
            .filter { it.rolleType == RolleType.BARN }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1", // TODO
                    type = "SOKNADSBARN_INFO",
                    innhold = POJONode(
                        SoknadsBarnNode(
                            soknadsbarnId = 1, // TODO
                            fodselsdato = it.fodtDato?.toLocalDate().toString(),
                        ),
                    ),
                )
            }

    fun prepareBostatus(behandling: Behandling): List<Grunnlag> =
        behandling
            .behandlingBarn
            .filter { (behandling.roller.filter { r -> r.rolleType == RolleType.BARN }.map { r -> r.ident }).toSet().contains(it.ident) }
            .flatMap { it.perioder }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1", // TODO
                    type = "BOSTATUS",
                    innhold = POJONode(
                        BostatusNode(
                            datoTil = it.fraDato.toString(),
                            datoFom = it.tilDato.toString(),
                            rolle = "SOKNADSBARN",
                            bostatusKode = "BOR_MED_FORELDRE", // TODO boStatus -> bostatusKode
                        ),
                    ),
                )
            }

    fun prepareBarnIHusstand(behandling: Behandling): List<Grunnlag> =
        behandling
            .behandlingBarn
            .filter { it.medISaken }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1", // TODO
                    type = "BARN_I_HUSSTAND",
                    innhold = POJONode( // TODO
                        BarnPeriodeNode(
                            datoTil = behandling.virkningsDato.toString(), // TODO
                            datoFom = behandling.datoTom.toString(), // TODO
                            antall = BigDecimal.TEN,
                        ),
                    ),
                )
            }

    fun prepareInntekterForBeregning(behandling: Behandling): List<Grunnlag> =
        behandling.inntekter
            .filter { it.taMed }
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1",
                    type = "INNTEKT",
                    innhold = POJONode(
                        InntektNode(
                            datoFom = it.datoFom?.toLocalDate().toString(),
                            datoTil = it.datoTom?.toLocalDate().toString(),
                            rolle = "BIDRAGSMOTTAKER",
                            inntektType = "INNTEKTSOPPLYSNINGER_ARBEIDSGIVER", // TODO vi kanskje trenger flere typer her
                            belop = it.belop,
                        ),
                    ),
                )
            } + behandling.barnetillegg.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoFom.toLocalDate().toString(),
                        datoTil = it.datoTom.toLocalDate().toString(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "EKSTRA_SMAABARNSTILLEGG",
                        belop = it.barnetillegg,
                    ),
                ),
            )
        } + behandling.utvidetbarnetrygd.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "INNTEKT",
                innhold = POJONode(
                    InntektNode(
                        datoFom = it.datoFom.toLocalDate().toString(),
                        datoTil = it.datoTom.toLocalDate().toString(),
                        rolle = "BIDRAGSMOTTAKER",
                        inntektType = "UTVIDET_BARNETRYGD",
                        belop = it.belop,
                    ),
                ),
            )
        }

    fun prepareSivilstand(behandling: Behandling): List<Grunnlag> =
        behandling.sivilstand.map {
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "SIVILSTAND",
                innhold = POJONode(
                    SivilstandNode(
                        datoFom = it.gyldigFraOgMed.toLocalDate().toString(),
                        datoTil = it.datoTom?.toLocalDate().toString(),
                        rolle = "BIDRAGSMOTTAKER",
                        sivilstandKode = it.sivilstandType.name,
                    ),
                ),
            )
        }

    fun preparePayload(behandling: Behandling): BeregnForskuddPayload {
        return BeregnForskuddPayload(
            beregnDatoFra = behandling.virkningsDato?.toLocalDate().toString(), // TODO kanskje behandling.datoFom?
            beregnDatoTil = behandling.datoTom.toLocalDate().toString(),
            grunnlagListe = prepareSoknadsBarn(behandling) +
                prepareBarnIHusstand(behandling) +
                prepareBostatus(behandling) +
                prepareInntekterForBeregning(behandling) +
                prepareSivilstand(behandling),

        )
    }
}

data class BarnPeriodeNode(
    val datoFom: String,
    val datoTil: String,
    val antall: BigDecimal,
)

data class BostatusNode(
    val datoFom: String,
    val datoTil: String,
    val rolle: String,
    val bostatusKode: String,
)

data class SoknadsBarnNode(
    val soknadsbarnId: Int,
    val fodselsdato: String?,
)

data class InntektNode(
    val datoFom: String?,
    val datoTil: String?,
    val rolle: String?,
    val inntektType: String?,
    val belop: BigDecimal?,
)

data class SivilstandNode(
    val datoFom: String?,
    val datoTil: String?,
    val rolle: String?,
    val sivilstandKode: String?,
)
