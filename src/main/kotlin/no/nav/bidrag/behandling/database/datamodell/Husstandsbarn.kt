package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
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
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDate

@Entity
open class Husstandsbarn(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    open val ident: String? = null,
    open val navn: String? = null,
    open val fødselsdato: LocalDate,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "husstandsbarn",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<Husstandsbarnperiode> = mutableSetOf(),
    @Column(name = "forrige_perioder", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var forrigePerioder: String? = null,
) {
    override fun toString(): String {
        return "Husstandsbarn(id=$id, ident=$ident, navn=$navn, fødselsdato=$fødselsdato, perioder(size)=${perioder.size}, " +
            "forrigePerioder=${forrigePerioder?.substring(0, maxOf(forrigePerioder!!.length, 10))}...)"
    }
}
