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
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(name = "rolle")
@SQLDelete(sql = "UPDATE rolle SET deleted = true WHERE id=?")
@SQLRestriction(value = "deleted=false")
open class Rolle(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val rolletype: Rolletype,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.ident")
    open var ident: String?,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.fødselsdato")
    open val fødselsdato: LocalDate,
    open val opprettet: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    // TODO: Migere persondata til Person-tabellen
    @Deprecated("Migrere til Person.navn")
    open val navn: String? = null,
    open val deleted: Boolean = false,
    open var innbetaltBeløp: BigDecimal? = null,
    @Column(name = "forrige_sivilstandshistorikk", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var forrigeSivilstandshistorikk: String? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "rolle",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var grunnlag: MutableSet<Grunnlag> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "rolle",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var notat: MutableSet<Notat> = mutableSetOf(),
    @OneToOne(
        fetch = FetchType.LAZY,
        mappedBy = "rolle",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open val husstandsmedlem: Husstandsmedlem? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(
        name = "person_id",
        nullable = true,
    )
    open var person: Person? = null,
) {
    val personident get() = person?.ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) }

    override fun toString(): String =
        "Rolle(id=$id, behandling=${behandling.id}, rolletype=$rolletype, ident=$ident, fødselsdato=$fødselsdato, opprettet=$opprettet, navn=$navn, deleted=$deleted, innbetaltBeløp=$innbetaltBeløp)"
}

fun Rolle.tilPersonident() = ident?.let { Personident(it) }

fun Rolle.tilNyestePersonident() = ident?.let { hentNyesteIdent(it) }

fun Rolle.hentNavn() = navn ?: hentPersonVisningsnavn(ident) ?: ""

fun Rolle.lagreSivilstandshistorikk(historikk: Set<Sivilstand>) {
    forrigeSivilstandshistorikk = commonObjectmapper.writeValueAsString(historikk.tilSerialiseringsformat())
}

fun Set<Sivilstand>.tilSerialiseringsformat() =
    this.map {
        SivilstandUtenBehandling(
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            kilde = it.kilde,
            sivilstand = it.sivilstand,
        )
    }

fun Rolle.henteLagretSivilstandshistorikk(behandling: Behandling): Set<Sivilstand> {
    val sivilstand =
        jsonListeTilObjekt<SivilstandUtenBehandling>(
            forrigeSivilstandshistorikk ?: oppdateringAvBoforholdFeilet(
                "Fant ikke tidligere lagret sivilstandshistorikk for " +
                    "bidragsmottaker i behandling ${behandling.id}",
            ),
        )

    return sivilstand
        .map {
            Sivilstand(
                datoFom = it.datoFom!!,
                datoTom = it.datoTom,
                kilde = it.kilde,
                sivilstand = it.sivilstand,
                behandling = behandling,
            )
        }.toSet()
}

data class SivilstandUtenBehandling(
    val datoFom: LocalDate? = null,
    val datoTom: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    val sivilstand: Sivilstandskode,
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
