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
import no.nav.bidrag.domene.enums.person.Bostatuskode
import java.time.LocalDate

@Entity(name = "barn_i_husstand_periode")
open class Husstandsbarnperiode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barn_i_husstand_id", nullable = false)
    open val husstandsbarn: Husstandsbarn,
    open val datoFom: LocalDate?,
    open val datoTom: LocalDate?,
    @Enumerated(EnumType.STRING)
    open val bostatus: Bostatuskode,
    @Enumerated(EnumType.STRING)
    open val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
)
