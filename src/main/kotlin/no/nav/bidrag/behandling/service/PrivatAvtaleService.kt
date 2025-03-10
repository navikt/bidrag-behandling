package no.nav.bidrag.behandling.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtalePeriode
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtalePeriodeDto
import no.nav.bidrag.behandling.dto.v2.privatavtale.OppdaterePrivatAvtaleRequest
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilPerson
import no.nav.bidrag.behandling.ugyldigForespørsel
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class PrivatAvtaleService(
    val behandlingService: BehandlingService,
    val personRepository: PersonRepository,
    val notatService: NotatService,
) {
    private fun lagrePrivatAvtale(
        behandling: Behandling,
        person: Person,
    ): PrivatAvtale {
        val privatAvtale = PrivatAvtale(behandling = behandling, person = person)
        behandling.privatAvtale.add(privatAvtale)
        return privatAvtale
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
        privatAvtale.skalIndeksreguleres = request.skalIndeksreguleres ?: privatAvtale.skalIndeksreguleres
        request.oppdaterPeriode?.let {
            oppdaterPrivatAvtaleAvtalePeriode(behandlingsid, privatavtaleId, it)
        }

        request.slettePeriodeId?.let {
            slettePrivatAvtaleAvtalePeriode(behandlingsid, privatavtaleId, it)
        }

        request.begrunnelse?.let {
            oppdaterPrivatAvtaleBegrunnelse(behandlingsid, privatavtaleId, it)
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
        privatavtaleId: Long,
        nyBegrunnelse: String,
    ) {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")

        notatService.oppdatereNotat(
            behandling,
            NotatGrunnlag.NotatType.PRIVAT_AVTALE,
            nyBegrunnelse,
            privatAvtale.barnetsRolleIBehandlingen ?: behandling.bidragspliktig!!,
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
        log.info { "Oppdaterer privat avtale periode ${request.id} i privat avtale $privatavtaleId i behandling $behandlingsid" }
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        val privatAvtale =
            behandling.privatAvtale.find { it.id == privatavtaleId }
                ?: ugyldigForespørsel("Fant ikke privat avtale med id $privatavtaleId i behandling $behandlingsid")

        if (request.id == null) {
            val nyPeriode =
                PrivatAvtalePeriode(
                    privatAvtale = privatAvtale,
                    beløp = request.beløp,
                    fom = request.periode.fom,
                    tom = request.periode.tom,
                )
            privatAvtale.perioder.add(nyPeriode)
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
        }
    }

    @Transactional
    fun opprettPrivatAvtale(
        behandlingsid: Long,
        gjelderBarn: BarnDto,
    ): PrivatAvtale {
        val behandling = behandlingService.hentBehandlingById(behandlingsid)
        behandling.privatAvtale
            .find { it.person.ident == gjelderBarn.personident?.verdi }
            ?.let {
                ugyldigForespørsel("Privat avtale for barn med personident ${gjelderBarn.personident?.verdi} finnes allerede")
            }
        return gjelderBarn.personident?.let { personidentBarn ->
            val rolleSøknadsbarn = behandling.søknadsbarn.find { it.ident == personidentBarn.verdi }
            personRepository.findFirstByIdent(personidentBarn.verdi)?.let { eksisterendePerson ->
                rolleSøknadsbarn?.let { eksisterendePerson.rolle.add(it) }
                rolleSøknadsbarn?.person = eksisterendePerson
                lagrePrivatAvtale(behandling, eksisterendePerson)
            } ?: run {
                val person =
                    Person(
                        ident = personidentBarn.verdi,
                        fødselsdato =
                            hentPersonFødselsdato(personidentBarn.verdi)
                                ?: fantIkkeFødselsdatoTilPerson(behandling.id!!),
                        rolle = rolleSøknadsbarn?.let { mutableSetOf(it) } ?: mutableSetOf(),
                    )
                person.rolle.forEach { it.person = person }

                lagrePrivatAvtale(behandling, person)
            }
        } ?: run {
            lagrePrivatAvtale(
                behandling,
                Person(navn = gjelderBarn.navn, fødselsdato = gjelderBarn.fødselsdato!!),
            )
        }
    }
}
