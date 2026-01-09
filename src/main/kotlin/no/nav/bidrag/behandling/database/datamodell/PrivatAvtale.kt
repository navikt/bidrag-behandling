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
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.transport.felles.toYearMonth
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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
    open var utenlandsk: Boolean = false,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "grunnlag_fra_vedtak_json")
    open var grunnlagFraVedtak: GrunnlagFraVedtak? = null,
    @Enumerated(EnumType.STRING)
    open var avtaleType: PrivatAvtaleType? = null,
    open var skalIndeksreguleres: Boolean = true,
    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST],
    )
    @JoinColumn(name = "person_id", nullable = false)
    open var person: Person? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = true)
    open var rolle: Rolle? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "privatAvtale",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<PrivatAvtalePeriode> = mutableSetOf(),
) {
    val personIdent get() = person?.ident ?: rolle!!.ident
    val personFødselsdato get() = person?.fødselsdato ?: rolle!!.fødselsdato
    val utledetAvtaledato get() =
        if (valgtVedtakFraNav != null) {
            valgtVedtakFraNav!!.vedtakstidspunkt?.withDayOfMonth(1)?.toLocalDate()
        } else {
            avtaleDato
        }
    val valgtVedtakFraNav get() =
        if (avtaleType == PrivatAvtaleType.VEDTAK_FRA_NAV) {
            rolle?.grunnlagFraVedtakForInnkreving?.takeIf { it.vedtak != null } ?: grunnlagFraVedtak?.takeIf { it.vedtak != null }
        } else {
            null
        }
    val perioderInnkreving get() =
        when {
            avtaleType == PrivatAvtaleType.VEDTAK_FRA_NAV -> {
                valgtVedtakFraNav
                    ?.perioder
                    ?.mapIndexed { i, it ->
                        PrivatAvtalePeriode(
                            i.toLong(),
                            this,
                            it.periode.fom.atDay(1),
                            it.periode.til?.atDay(1),
                            it.beløp ?: BigDecimal.ZERO,
                        )
                    }?.toSet() ?: emptySet()
            }

            else -> {
                perioder
            }
        }

    override fun toString(): String =
        "PrivatAvtale(id=$id, behandling=${behandling.id}, rolle=${rolle?.id} person=${person?.id}, perioder=$perioder)"
}
