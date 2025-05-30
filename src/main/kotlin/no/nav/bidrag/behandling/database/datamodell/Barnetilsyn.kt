package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.domene.enums.barnetilsyn.Tilsynstype
import no.nav.bidrag.domene.enums.diverse.Kilde
import java.time.LocalDate

@Entity
open class Barnetilsyn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underholdskostnad_id", nullable = false)
    open val underholdskostnad: Underholdskostnad,
    open var fom: LocalDate,
    open var tom: LocalDate? = null,
    open var under_skolealder: Boolean? = null,
    @Enumerated(EnumType.STRING)
    open var omfang: Tilsynstype,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
) {
    override fun toString(): String =
        "Barnetilsyn(id=$id, underholdskostnad=${underholdskostnad.id}, fom=$fom, tom=$tom, under_skolealder=$under_skolealder, omfang=$omfang, kilde=$kilde)"
}
