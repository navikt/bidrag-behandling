package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany

@Entity
open class Underholdskostnad(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) open val id: Long? = null,
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(
        name = "behandling_id",
        nullable = false,
    )
    open val behandling: Behandling,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "person_id", nullable = false)
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
)
