package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.domene.enums.diverse.Kilde
import java.time.LocalDate

@Entity
open class Barnetilsyn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underholdskostnad_id", nullable = false)
    open val underholdskostnad: Underholdskostnad,
    open val fom: LocalDate,
    open val tom: LocalDate? = null,
    open val omfang: Omfang,
    open val kilde: Kilde,
)

enum class Omfang { HELTID, DELTID }
