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
import jakarta.persistence.OneToOne
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnFra

@Entity
open class Samvær(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    @JoinColumn(name = "rolle_id", nullable = true)
    open val rolle: Rolle,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "samvær",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<Samværsperiode> = mutableSetOf(),
) {
    fun erLik(other: Samvær): Boolean {
        if (rolle.finnBeregnFra() != other.rolle.finnBeregnFra()) return false
        if (this === other) return true
        if (perioder.size != other.perioder.size) return false
        if (!perioder.all { periode -> other.perioder.any { otherPeriode -> periode.erLik(otherPeriode) } }) return false
        return true
    }

    override fun toString(): String = "Samvær(id=$id, behandlingId=${behandling.id}, perioder(size)=${perioder.size}"
}
