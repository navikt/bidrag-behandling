package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtalePeriode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.database.repository.PrivatavtaleRepository
import no.nav.bidrag.behandling.database.repository.RolleRepository
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleBegrunnelseRequest
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleRequest
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class PrivatAvtaleService(
    val behandlingService: BehandlingService,
    val notatService: NotatService,
    val personRepository: PersonRepository,
    val privatavtaleRepository: PrivatavtaleRepository? = null,
    val rolleRepository: RolleRepository? = null,
) {
    @Transactional
    fun fiksReferanserPrivatAvtale() {
        val privatAvtaler = privatavtaleRepository!!.hentPrivatAvtalerMedFeilReferanse()

        secureLogger.info { "Fant ${privatAvtaler.size} som har feil referanse i rolle" }
        privatAvtaler.filter { it.rolle != null }.forEach { privatAvtale ->
            val riktigRolle =
                rolleRepository!!.findRolleIdByBehandlingIdAndRolleIdent(
                    privatAvtale.behandling.id!!,
                    privatAvtale.rolle!!.ident!!,
                )
            secureLogger.info {
                "Oppdaterer privatavtale ${privatAvtale.id} i behandling ${privatAvtale.behandling.id} som hadde rolle ${privatAvtale.rolle!!.id} men som burde ha rolleId ${riktigRolle!!.id}"
            }
            privatAvtale.rolle = riktigRolle
        }
    }

    private fun lagrePrivatAvtale(
        behandling: Behandling,
        rolle: Rolle? = null,
        person: Person? = null,
    ): PrivatAvtale {
        val privatAvtale =
            PrivatAvtale(behandling = behandling, rolle = rolle, person = person, avtaleType = PrivatAvtaleType.PRIVAT_AVTALE)
        behandling.privatAvtale.add(privatAvtale)
        return privatAvtale
    }

    @Transactional
    fun oppdaterPrivatAvtaleBegrunnelse(
        behandlingsid: Long,
        request: OppdaterePrivatAvtaleBegrunnelseRequest,
    ) {
        log.info { "Oppdaterer privatavtale begrunnelse ${request.privatavtaleid} i behandling $behandlingsid" }
        request.begrunnelse?.let {
            oppdaterPrivatAvtaleBegrunnelse(behandlingsid, request.privatavtaleid, request.barnIdent, request.barnId, it)
        }
    }

    @Transactional
    fun oppdaterPrivatAvtale(
        behandlingsid: Long,
        privatavtaleId: Long,
        request: OppdaterePrivatAvtaleRequest,
    ) {
        log.info { "Oppdaterer privatavtale $privatavtaleId i behandling $behandlingsid" }
        secureLogger.info { "Oppdaterer privatavtale $privatavtaleId i behandling $behandlingsid: $request" }
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")
        privatAvtale.avtaleDato = request.avtaleDato ?: privatAvtale.avtaleDato
        privatAvtale.avtaleType = request.avtaleType ?: privatAvtale.avtaleType
        privatAvtale.utenlandsk = request.gjelderUtland ?: privatAvtale.utenlandsk

        request.oppdaterPeriode?.let {
            oppdaterPrivatAvtaleAvtalePeriode(behandlingsid, privatavtaleId, it)
        }

        request.slettePeriodeId?.let {
            slettePrivatAvtaleAvtalePeriode(behandlingsid, privatavtaleId, it)
        }

        request.begrunnelse?.let {
            oppdaterPrivatAvtaleBegrunnelse(behandlingsid, privatavtaleId, null, null, it)
        }

        privatAvtale.skalIndeksreguleres =
            if (privatAvtale.utenlandsk &&
                !privatAvtale.erAllePerioderNorsk
            ) {
                false
            } else {
                request.skalIndeksreguleres ?: privatAvtale.skalIndeksreguleres
            }
    }

    @Transactional
    fun slettPrivatAvtale(
        behandlingsid: Long,
        privatavtaleId: Long,
    ) {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")
        behandling.privatAvtale.remove(privatAvtale)
    }

    @Transactional
    fun oppdaterPrivatAvtaleBegrunnelse(
        behandlingsid: Long,
        privatavtaleId: Long?,
        barnIdent: String?,
        barnId: Long?,
        nyBegrunnelse: String,
    ) {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val rolle = behandling.roller.find { it.ident == barnIdent || it.id == barnId }
        val privatAvtale =
            if (privatavtaleId != null) {
                behandling.privatAvtale.find { it.id == privatavtaleId }
                    ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")
            } else {
                null
            }

        notatService.oppdatereNotat(
            behandling,
            NotatGrunnlag.NotatType.PRIVAT_AVTALE,
            nyBegrunnelse,
            rolle ?: privatAvtale?.rolle ?: behandling.bidragspliktig!!,
        )
    }

    @Transactional
    fun slettePrivatAvtaleAvtalePeriode(
        behandlingsid: Long,
        privatavtaleId: Long,
        periodeId: Long,
    ) {
        log.info { "Sletter privat avtale periode $periodeId fra privat avtale $privatavtaleId i behandling $behandlingsid" }
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")

        val periode =
            privatAvtale.perioder.find { it.id == periodeId }
                ?: ugyldigForespørsel(
                    "Fant ikke periode med id $periodeId i privat avtale med id $privatavtaleId i behandling $behandlingsid",
                )
        privatAvtale.perioder.remove(periode)
    }

    @Transactional
    fun oppdaterPrivatAvtaleAvtalePeriode(
        behandlingsid: Long,
        privatavtaleId: Long,
        request: OppdaterePrivatAvtalePeriodeDto,
    ) {
        log.debug { "Oppdaterer privat avtale periode ${request.id} i privat avtale $privatavtaleId i behandling $behandlingsid" }
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")

        if (request.id == null) {
            privatAvtale.perioder.filter { it.fom < request.periode.fom }.maxByOrNull { it.fom }?.let {
                it.tom = request.periode.fom.minusDays(1)
            }
            val nyPeriode =
                PrivatAvtalePeriode(
                    privatAvtale = privatAvtale,
                    beløp = request.beløp,
                    fom = request.periode.fom,
                    tom = request.periode.tom,
                    valutakode = request.valutakode,
                    samværsklasse = request.samværsklasse,
                )
            privatAvtale.perioder.add(nyPeriode)
            // Adjust the new period's tom if there's a period coming after
            privatAvtale.perioder.filter { it.fom > nyPeriode.fom }.minByOrNull { it.fom }?.let { nextPeriode ->
                nyPeriode.tom = nextPeriode.fom.minusDays(1)
            }
        } else {
            val eksisterendePeriode =
                privatAvtale.perioder.find { it.id == request.id }
                    ?: ugyldigForespørsel(
                        "Fant ikke periode med id ${request.id} " +
                            "i privat avtale med id $privatavtaleId i behandling $behandlingsid",
                    )
            eksisterendePeriode.beløp = request.beløp
            eksisterendePeriode.fom = request.periode.fom
            eksisterendePeriode.tom = request.periode.tom
            eksisterendePeriode.valutakode = request.valutakode
            eksisterendePeriode.samværsklasse = request.samværsklasse
        }
    }

    @Transactional
    fun opprettPrivatAvtale(
        behandlingsid: Long,
        gjelderBarn: BarnDto,
    ): PrivatAvtale {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        behandling.privatAvtale
            .find { it.rolle?.ident == gjelderBarn.personident?.verdi || it.person?.ident == gjelderBarn.personident?.verdi }
            ?.let {
                ugyldigForespørsel("Privat avtale for barn med personident ${gjelderBarn.personident?.verdi} finnes allerede")
            }
        return gjelderBarn.personident?.let { personidentBarn ->
            behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }?.let {
                lagrePrivatAvtale(behandling, it)
            }
        } ?: run {
            val person =
                gjelderBarn.personident?.let { personRepository.findFirstByIdent(it.verdi) } ?: Person(
                    navn = gjelderBarn.navn,
                    fødselsdato =
                        gjelderBarn.fødselsdato ?: hentPersonFødselsdato(gjelderBarn.personident!!.verdi)!!,
                    ident = gjelderBarn.personident?.verdi,
                )
            lagrePrivatAvtale(
                behandling,
                person = person,
            )
        }
    }
}
