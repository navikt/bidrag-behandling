package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Samvær
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværskalkulatorBeregningDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværsperiodeDto
import no.nav.bidrag.behandling.dto.v2.samvær.SletteSamværsperiodeElementDto
import no.nav.bidrag.behandling.dto.v2.samvær.valider
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class SamværService(
    private val samværRepository: SamværRepository,
    private val behandlingService: BehandlingService,
    private val notatService: NotatService,
) {
    fun oppdaterSamvær(
        behandlingsid: Long,
        request: OppdaterSamværDto,
    ): OppdaterSamværResponsDto {
        // val

        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        request.valider()
        log.info { "Oppdaterer samvær for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer samvær for behandling $behandlingsid, forespørsel=$request" }
        val oppdaterSamvær = behandling.samvær.finnSamværForBarn(request.gjelderBarn)

        request.run {
            periode?.let { oppdaterPeriode(it, oppdaterSamvær) }
            oppdatereBegrunnelse?.let {
                notatService.oppdatereNotat(
                    behandling,
                    NotatGrunnlag.NotatType.SAMVÆR,
                    it.henteNyttNotat() ?: "",
                    oppdaterSamvær.rolle.id!!,
                )
            }
        }

        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    private fun oppdaterPeriode(
        request: OppdaterSamværsperiodeDto,
        oppdaterSamvær: Samvær,
    ) {
        if (request.id == null) {
            val nyPeriode =
                Samværsperiode(
                    oppdaterSamvær,
                    request.periode.fom,
                    request.periode.tom,
                    request.samværsklasse!!,
                    beregningJson = request.beregning.tilJsonString(),
                )
            oppdaterSamvær.perioder.add(nyPeriode)
//            oppdaterSamvær.perioder
//                .maxByOrNull { it.fom }
//                ?.let {
//                    it.tom = nyPeriode.fom.minusDays(1)
//                }
        } else {
            val oppdaterPeriode =
                oppdaterSamvær.perioder.find { it.id == request.id }
                    ?: ugyldigForespørsel("Fant ikke samværsperiode med id ${request.id} i samvær ${oppdaterSamvær.id}")
            oppdaterPeriode.fom = request.periode.fom
            oppdaterPeriode.tom = request.periode.tom
            oppdaterPeriode.samværsklasse = request.samværsklasse ?: oppdaterPeriode.samværsklasse
        }
    }

    fun slettPeriode(
        behandlingsid: Long,
        request: SletteSamværsperiodeElementDto,
    ): OppdaterSamværResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        log.info { "Sletter samværsperiode $request for behandling $behandlingsid" }
        val oppdaterSamvær =
            behandling.samvær.finnSamværForBarn(request.gjelderBarn)
        val slettPeriode =
            oppdaterSamvær.hentPeriode(request.samværsperiodeId)

        oppdaterSamvær.perioder.remove(slettPeriode)
        log.info { "Slettet samværsperiode ${slettPeriode.id} fra samvær ${oppdaterSamvær.id} i behandling $behandlingsid" }
        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    fun slettSamværskalkulatorBeregning(
        behandlingsid: Long,
        request: SletteSamværsperiodeElementDto,
    ): OppdaterSamværResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        log.info { "Sletter samværskalkulator beregning $request for behandling $behandlingsid" }
        val oppdaterSamvær =
            behandling.samvær.finnSamværForBarn(request.gjelderBarn)
        val periode =
            oppdaterSamvær.hentPeriode(request.samværsperiodeId)

        periode.beregningJson = null
        log.info {
            "Slettet samværskalkulator beregning fra periode ${periode.id} i samvær ${oppdaterSamvær.id} i behandling $behandlingsid"
        }
        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    private fun SamværskalkulatorDetaljer?.tilJsonString() = this?.let { commonObjectmapper.writeValueAsString(it) }

    fun oppdaterSamværsperiodeBeregning(
        behandlingsid: Long,
        request: OppdaterSamværskalkulatorBeregningDto,
    ): OppdaterSamværResponsDto {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        log.info { "Oppdaterer beregning for samværsperiode $request for behandling $behandlingsid" }
        val oppdaterSamvær =
            behandling.samvær.find { it.rolle.ident == request.gjelderBarn }
                ?: ugyldigForespørsel("Fant ikke samvær for barn ${request.gjelderBarn} i behandling med id $behandlingsid")
        val oppdaterPeriode =
            oppdaterSamvær.perioder.find { it.id == request.samværsperiodeId }
                ?: ugyldigForespørsel("Fant ikke samværsperiode med id ${request.samværsperiodeId} i samvær ${oppdaterSamvær.id}")

        oppdaterPeriode.beregningJson = request.beregning.tilJsonString()
        oppdaterPeriode.samværsklasse = beregnSamværsklasse(request.beregning)
        return samværRepository.save(oppdaterSamvær).tilOppdaterSamværResponseDto()
    }

    fun beregnSamværsklasse(kalkulator: SamværskalkulatorDetaljer): Samværsklasse {
        val bpNetter =
            kalkulator.ferier.sumOf {
                it.bidragspliktigTotalAntallNetterOverToÅr
            }
        val bmNetter = kalkulator.ferier.sumOf { it.bidragsmottakerTotalAntallNetterOverToÅr }
        val gjennomsnittMånedlig = (bmNetter + bpNetter) / 24

        return Samværsklasse.SAMVÆRSKLASSE_1
    }

    private fun MutableSet<Samvær>.finnSamværForBarn(gjelderBarn: String) =
        find { it.rolle.ident == gjelderBarn }
            ?: ugyldigForespørsel("Fant ikke samvær for barn $gjelderBarn i behandling med id ${firstOrNull()?.behandling?.id}")

    private fun Samvær.hentPeriode(id: Long) =
        perioder.find { it.id == id }
            ?: ugyldigForespørsel("Fant ikke samværsperiode med id $id i samvær $id")
}
