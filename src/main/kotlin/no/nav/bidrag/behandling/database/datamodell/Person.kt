package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
open class Person(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    open var ident: String? = null,
    open val navn: String? = null,
    open val fødselsdato: LocalDate,
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
    fun opphørsdatoForRolle(behandling: Behandling) = behandling.roller.find { it.person?.id == id }?.opphørsdatoYearMonth

    fun finnRolle(behandling: Behandling) = behandling.roller.find { it.person?.id == id }

    val personident get() = ident?.let { Personident(it) } ?: rolle.firstOrNull()?.ident?.let { Personident(it) }

    val henteFødselsdato get() = fødselsdato ?: rolle.firstOrNull()?.fødselsdato

    override fun toString(): String =
        "Person(id=$id, ident=$ident, navn=$navn, fødselsdato=$fødselsdato, opprettet=$opprettet, roller=$rolle)"
}
