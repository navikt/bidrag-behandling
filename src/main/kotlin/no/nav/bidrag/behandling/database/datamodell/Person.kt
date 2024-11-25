package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
open class Person(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    open val ident: String? = null,
    open val navn: String? = null,
    open val fødselsdato: LocalDate? = null,
    open val opprettet: LocalDateTime = LocalDateTime.now(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "person",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open val underholdskostnad: MutableSet<Underholdskostnad> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "person",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = false,
    )
    open val rolle: MutableSet<Rolle> = mutableSetOf(),
) {
    override fun toString(): String =
        "Person(id=$id, ident=$ident, navn=$navn, fødselsdato=$fødselsdato, opprettet=$opprettet, roller=$rolle)"
}
