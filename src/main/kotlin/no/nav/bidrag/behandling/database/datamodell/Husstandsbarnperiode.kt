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
<<<<<<< HEAD
import java.time.LocalDate
=======
import java.util.Date
>>>>>>> main

@Entity(name = "barn_i_husstand_periode")
class Husstandsbarnperiode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barn_i_husstand_id", nullable = false)
    val husstandsbarn: Husstandsbarn,
    val datoFom: LocalDate?,
    val datoTom: LocalDate?,
    @Enumerated(EnumType.STRING)
<<<<<<< HEAD
    val bostatus: Bostatuskode,
=======
    val bostatus: Bostatuskode?,
>>>>>>> main
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
