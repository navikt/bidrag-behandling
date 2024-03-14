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
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import java.time.LocalDate

@Entity(name = "barn_i_husstand")
open class Husstandsbarn(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    open val ident: String? = null,
    open val navn: String? = null,
    open val foedselsdato: LocalDate,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "husstandsbarn",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<Husstandsbarnperiode> = mutableSetOf(),
)

fun Husstandsbarn.hentNavn() = navn ?: hentPersonVisningsnavn(ident)
