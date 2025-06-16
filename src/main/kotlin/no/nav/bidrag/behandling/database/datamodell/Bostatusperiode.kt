package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import java.time.LocalDate

@Entity
@JsonIgnoreProperties(value = ["husstandsmedlem", "id"])
open class Bostatusperiode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "husstandsmedlem_id", nullable = false)
    open var husstandsmedlem: Husstandsmedlem,
    open var datoFom: LocalDate?,
    open var datoTom: LocalDate?,
    @Enumerated(EnumType.STRING)
    open val bostatus: Bostatuskode,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
) {
    override fun toString(): String = "Bostatusperiode(id=$id, datoFom=$datoFom, datoTom=$datoTom, bostatus=$bostatus, kilde=$kilde)"
}
