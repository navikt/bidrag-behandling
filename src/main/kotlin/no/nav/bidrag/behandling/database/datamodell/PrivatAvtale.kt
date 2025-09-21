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
import no.nav.bidrag.transport.felles.toYearMonth
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
    open val person: Person? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = true)
    open val rolle: Rolle? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "privatAvtale",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<PrivatAvtalePeriode> = mutableSetOf(),
) {
    val utledetAvtaledato get() =
        if (valgtVedtakFraNav != null) {
            valgtVedtakFraNav!!.vedtakstidspunkt?.withDayOfMonth(1)?.toLocalDate()
        } else {
            avtaleDato
        }
    val valgtVedtakFraNav get() =
        rolle!!.grunnlagFraVedtakForInnkreving?.takeIf {
            it.vedtak != null
        }
    val perioderInnkreving get() =
        when {
            behandling.erInnkreving && avtaleType == PrivatAvtaleType.VEDTAK_FRA_NAV ->
                valgtVedtakFraNav
                    ?.perioder
                    ?.filter {
                        it.periode.til == null ||
                            it.periode.fom.isAfter(rolle!!.virkningstidspunkt!!.toYearMonth())
                    }?.mapIndexed { i, it ->
                        PrivatAvtalePeriode(
                            i.toLong(),
                            this,
                            maxOf(it.periode.fom.atDay(1), rolle!!.virkningstidspunkt!!),
                            it.periode.til?.atDay(1),
                            it.beløp ?: BigDecimal.ZERO,
                        )
                    }?.toSet() ?: emptySet()
            else -> perioder
        }

    override fun toString(): String =
        "PrivatAvtale(id=$id, behandling=${behandling.id}, rolle=${rolle?.id} person=${person?.id}, perioder=$perioder)"
}
