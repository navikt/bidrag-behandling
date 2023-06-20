package no.nav.bidrag.behandling.controller

import arrow.core.Either
import com.fasterxml.jackson.databind.node.POJONode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import no.nav.bidrag.behandling.consumer.BeregnForskuddPayload
import no.nav.bidrag.behandling.consumer.BidragBeregnForskuddConsumer
import no.nav.bidrag.behandling.consumer.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.BoStatusType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.RolleType
import no.nav.bidrag.behandling.dto.behandling.ForskuddDto
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
    fun beregnForskudd(@PathVariable behandlingId: Long): List<ForskuddDto> {
        val behandling = behandlingService.hentBehandlingById(behandlingId)

        val results = behandling
            .roller
            .filter { RolleType.BARN == it.rolleType }
            .map {
                Either.catch {
                    bidragBeregnForskuddConsumer.beregnForskudd(preparePayload(behandling, it))
                }
            }

        results
            .mapNotNull { it.leftOrNull() }
            .forEach {
                // LOGGING Errors here
                LOGGER.warn { it }
            }

        return results
            .mapNotNull { it.getOrNull() }
    }

    fun prepareSoknadsBarn(behandling: Behandling, soknadBarn: Rolle): List<Grunnlag> =
        listOf(
            Grunnlag(
                referanse = "Mottatt_ref1",
                type = "SOKNADSBARN_INFO",
                innhold = POJONode(
                    SoknadsBarnNode(
                        soknadsbarnId = soknadBarn.soknadsLinje,
                        fodselsdato = soknadBarn.fodtDato?.toLocalDate().toString(),
                    ),
                ),
            ),
        )

    fun prepareBostatus(behandling: Behandling, soknadsBarn: Rolle): List<Grunnlag> =
        behandling
            .behandlingBarn
            .filter { soknadsBarn.ident == it.ident }
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

    // TODO PERIODER!
    fun prepareBarnIHusstand(behandling: Behandling): List<Grunnlag> =
        behandling
            .behandlingBarn
            .filter { it.medISaken }
            .map { it.perioder.filter { it.boStatus == BoStatusType.DOKUMENTERT_BOENDE_HOS_BM } }
            .flatten()
            .map {
                Grunnlag(
                    referanse = "Mottatt_ref1",
                    type = "BARN_I_HUSSTAND",
                    innhold = POJONode(
                        BarnPeriodeNode(
                            datoTil = behandling.virkningsDato.toString(),
                            datoFom = behandling.datoTom.toString(),
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
                        sivilstandKode = it.sivilstandType.name,
                    ),
                ),
            )
        }

    fun preparePayload(behandling: Behandling, soknadsBarn: Rolle): BeregnForskuddPayload =
        BeregnForskuddPayload(
            beregnDatoFra = behandling.virkningsDato?.toLocalDate().toString(), // TODO kanskje behandling.datoFom?
            beregnDatoTil = behandling.datoTom.toLocalDate().toString(),
            grunnlagListe = prepareSoknadsBarn(behandling, soknadsBarn) +
                prepareBarnIHusstand(behandling) +
                prepareBostatus(behandling, soknadsBarn) +
                prepareInntekterForBeregning(behandling) +
                prepareSivilstand(behandling),

        )
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
    val sivilstandKode: String?,
    val rolle: String = "BIDRAGSMOTTAKER",
)
