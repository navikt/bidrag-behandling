package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany

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
