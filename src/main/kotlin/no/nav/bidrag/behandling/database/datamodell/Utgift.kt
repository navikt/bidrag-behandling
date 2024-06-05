package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.SequenceGenerator
import org.hibernate.annotations.ColumnTransformer
import java.math.BigDecimal

@Entity
open class Utgift(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "utgift_id_seq")
    @SequenceGenerator(name = "utgift_id_seq", sequenceName = "utgift_id_seq", initialValue = 1, allocationSize = 1)
    open var id: Long? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "utgift",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var utgiftsposter: MutableSet<Utgiftspost> = mutableSetOf(),
    open var beløpDirekteBetaltAvBp: BigDecimal = BigDecimal.ZERO,
    @Column(name = "forrige_historikk", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var forrigeUtgiftsposterHistorikk: String? = null,
) {
    override fun toString(): String {
        val forrigeHistorikkString =
            forrigeUtgiftsposterHistorikk?.substring(
                0,
                maxOf(forrigeUtgiftsposterHistorikk!!.length, 10),
            ) ?: ""
        return "Utgift(id=$id, behandlingId=${behandling.id}, beløpBetaltAvBp=$beløpDirekteBetaltAvBp, " +
            " utgiftsposter(size)=${utgiftsposter.size}, " +
            "forrigeUtgiftsposterHistorikk=$forrigeHistorikkString...)"
    }
}
