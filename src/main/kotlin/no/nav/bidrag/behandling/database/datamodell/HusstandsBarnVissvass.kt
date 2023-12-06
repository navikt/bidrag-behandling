package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.time.LocalDate

@Entity(name = "barn_i_husstand")
class HusstandsbarnVissvass(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,
    @Column(name = "med_i_saken")
    val medISaken: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val ident: String? = null,
    val navn: String? = null,
    val foedselsdato: LocalDate,
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "husstandsbarn", cascade = [CascadeType.ALL], orphanRemoval = true)
    var perioder: MutableSet<Husstandsbarnperiode> = mutableSetOf(),
)
