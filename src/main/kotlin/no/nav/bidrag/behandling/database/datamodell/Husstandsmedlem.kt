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
import jakarta.persistence.OneToOne
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDate

@Entity
open class Husstandsmedlem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.ident")
    open var ident: String? = null,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.navn")
    open val navn: String? = null,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.fødselsdato")
    // kan bare være null dersom koblingen mot rolle er satt
    open val fødselsdato: LocalDate? = null,
    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    @JoinColumn(name = "rolle_id", nullable = true)
    open val rolle: Rolle? = null,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "husstandsmedlem",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var perioder: MutableSet<Bostatusperiode> = mutableSetOf(),
    @Column(name = "forrige_perioder", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var forrigePerioder: String? = null,
) {
    val erSøknadsbarn get() = behandling.søknadsbarn.any { it.ident == ident }

    override fun toString(): String =
        "Husstandsmedlem(id=$id, behandlingId=${behandling.id}, ident=$ident, navn=$navn, fødselsdato=$fødselsdato," +
            " perioder(size)=${perioder.size}, kilde=$kilde, " +
            "forrigePerioder=${forrigePerioder?.substring(0, maxOf(forrigePerioder!!.length, 10)) ?: ""}...)"
}
