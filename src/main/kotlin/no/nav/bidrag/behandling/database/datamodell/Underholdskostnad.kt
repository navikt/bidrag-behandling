package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import no.nav.bidrag.domene.enums.diverse.Kilde

@Entity
open class Underholdskostnad(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(
        name = "behandling_id",
        nullable = false,
    )
    open val behandling: Behandling,
    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST],
    )
    @JoinColumn(name = "person_id", nullable = false)
    open val person: Person,
    open var harTilsynsordning: Boolean? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    ) open val barnetilsyn: MutableSet<Barnetilsyn> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open val tilleggsstønad: MutableSet<Tilleggsstønad> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open val faktiskeTilsynsutgifter: MutableSet<FaktiskTilsynsutgift> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde? = null,
) {
    val opphørsdato get() = barnetsRolleIBehandlingen?.opphørsdato ?: behandling.globalOpphørsdato
    val barnetsRolleIBehandlingen get() = person.rolle.find { behandling.id == it.behandling.id }

    override fun toString(): String =
        "Underholdskostnad(id=$id, behandling=${behandling.id}, person=${person.id}, harTilsynsordning=$harTilsynsordning, faktiskeTilsynsutgifter=$faktiskeTilsynsutgifter, barnetilsyn=$barnetilsyn, tilleggsstønad=$tilleggsstønad)"
}
