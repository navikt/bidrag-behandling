package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.repository.SamværRepository
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværDto
import no.nav.bidrag.behandling.dto.v2.samvær.OppdaterSamværResponsDto
import no.nav.bidrag.behandling.dto.v2.samvær.valider
import no.nav.bidrag.behandling.transformers.samvær.tilOppdaterSamværResponseDto
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
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
        forespørsel: OppdaterSamværDto,
    ): OppdaterSamværResponsDto {
        // val

        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        forespørsel.valider()
        log.info { "Oppdaterer samvær for behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer samvær for behandling $behandlingsid, forespørsel=$forespørsel" }
        val oppdaterSamvær =
            behandling.samvær.find { it.rolle.ident == forespørsel.gjelderBarn }
                ?: ugyldigForespørsel("Fant ikke samvær for barn ${forespørsel.gjelderBarn} i behandling med id $behandlingsid")

        forespørsel.run {
            if (slettPeriode != null) {
                behandling.samvær.remove(oppdaterSamvær)
                log.info { "Slett samvær ${oppdaterSamvær.id} fra behandling $behandlingsid" }
            } else if (periode != null) {
                if (periode.id == null) {
                    val nyPeriode = Samværsperiode(oppdaterSamvær, periode.periode.fom, periode.periode.til, periode.samværsklasse)
                    oppdaterSamvær.perioder.add(nyPeriode)
                } else {
                    val oppdaterPeriode =
                        oppdaterSamvær.perioder.find { it.id == periode.id }
                            ?: ugyldigForespørsel("Fant ikke samværperiode med id ${periode.id} i samvær ${oppdaterSamvær.id}")
                    oppdaterPeriode.datoFom = periode.periode.fom
                    oppdaterPeriode.datoTom = periode.periode.til
                    oppdaterPeriode.samværsklasse = periode.samværsklasse
                }
            }
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

    fun beregnSamværsklasse(kalkulator: SamværskalkulatorDetaljer): Samværsklasse {
        val bpNetter =
            kalkulator.ferier.sumOf {
                it.bidragspliktig.totalAntallNetterOverToÅr
            }
        val bmNetter = kalkulator.ferier.sumOf { it.bidragsmottaker.totalAntallNetterOverToÅr }
        val gjennomsnittMånedlig = (bmNetter + bpNetter) / 24

        return Samværsklasse.SAMVÆRSKLASSE_1
    }
}
