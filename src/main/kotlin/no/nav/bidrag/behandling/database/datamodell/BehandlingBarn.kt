package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
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

@Entity(name = "behandling_barn")
data class BehandlingBarn(
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
    val foedselsDato: Date? = null,
) {
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandlingBarn", cascade = [CascadeType.ALL], orphanRemoval = true)
    var perioder: MutableSet<BehandlingBarnPeriode> = mutableSetOf()
}
