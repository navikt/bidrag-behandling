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
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(name = "privat_avtale")
open class PrivatAvtale(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "behandling_id",
        nullable = false,
    )
    open val behandling: Behandling,
    open var avtaleDato: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    open var avtaleType: PrivatAvtaleType? = null,
    open var skalIndeksreguleres: Boolean = true,
    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST],
    )
    @JoinColumn(name = "person_id", nullable = false)
    open val person: Person,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "privatAvtale",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<PrivatAvtalePeriode> = mutableSetOf(),
) {
    val barnetsRolleIBehandlingen get() = person.rolle.find { behandling.id == it.behandling.id }

    val perioderInnkreving get() =
        when {
            behandling.erInnkreving && avtaleType == PrivatAvtaleType.VEDTAK_FRA_NAV ->
                barnetsRolleIBehandlingen!!.grunnlagFraVedtakListe.find { it.aldersjusteringForÅr == null }?.perioder?.mapIndexed { i, it ->
                    PrivatAvtalePeriode(
                        i.toLong(),
                        this,
                        it.periode.fom.atDay(1),
                        it.periode.til?.atDay(1),
                        it.beløp ?: BigDecimal.ZERO,
                    )
                } ?: emptyList()
            else -> perioder
        }

    override fun toString(): String = "PrivatAvtale(id=$id, behandling=${behandling.id}, person=${person.id}, perioder=$perioder)"
}
