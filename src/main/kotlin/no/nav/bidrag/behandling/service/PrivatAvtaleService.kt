package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.dto.v2.underhold.BarnDto
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilPerson
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PrivatAvtaleService(
    val behandlingService: BehandlingService,
    val personRepository: PersonRepository,
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
    fun opprettPrivatAvtale(
        behandling: Behandling,
        gjelderBarn: BarnDto,
    ): PrivatAvtale =
        gjelderBarn.personident?.let { personidentBarn ->
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
