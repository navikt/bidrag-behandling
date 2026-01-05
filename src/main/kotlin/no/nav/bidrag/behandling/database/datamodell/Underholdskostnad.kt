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
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnFra
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.felles.toLocalDate
import org.hibernate.annotations.JoinFormula
import java.time.LocalDate

@Entity
open class Underholdskostnad(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(
        name = "behandling_id",
        nullable = false,
    )
    open val behandling: Behandling,
    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST],
    )
    @JoinColumn(name = "person_id", nullable = false)
    open var person: Person? = null,
    @ManyToOne(
        fetch = FetchType.LAZY,
    )
    @JoinColumn(name = "rolle_id", nullable = false)
    open var rolle: Rolle? = null,
    open var harTilsynsordning: Boolean? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    ) open var barnetilsyn: MutableSet<Barnetilsyn> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var tilleggsstønad: MutableSet<Tilleggsstønad> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "underholdskostnad",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var faktiskeTilsynsutgifter: MutableSet<FaktiskTilsynsutgift> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde? = null,
) {
    val gjelderAndreBarn get() = rolle == null || rolle!!.rolletype == Rolletype.BIDRAGSMOTTAKER
    val beregnFraDato get() = rolle?.finnBeregnFra()?.toLocalDate() ?: behandling.virkningstidspunktEllerSøktFomDato
    val personNavn: String? get() = person?.navn ?: rolle?.navn
    val personIdent: String? get() = if (rolle?.rolletype == Rolletype.BARN) rolle?.ident else person?.ident
    val personFødselsdato: LocalDate get() = person?.fødselsdato ?: rolle?.fødselsdato!!
    val opphørsdato get() = rolle?.opphørsdato ?: behandling.globalOpphørsdato

    override fun toString(): String =
        "Underholdskostnad(id=$id, behandling=${behandling.id}, person=${person?.id}, harTilsynsordning=$harTilsynsordning, faktiskeTilsynsutgifter=$faktiskeTilsynsutgifter, barnetilsyn=$barnetilsyn, tilleggsstønad=$tilleggsstønad)"
}
