package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Person
import org.springframework.data.repository.CrudRepository

interface PersonRepository: CrudRepository<Person, Long> {

    fun findFirstByIdent(ident: String): Person?
}