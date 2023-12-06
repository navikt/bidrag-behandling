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
<<<<<<< HEAD
import java.time.LocalDate
=======
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import java.util.Date
>>>>>>> main

// TODO: koble sammen med rolletabellen
@Entity(name = "sivilstand")
class Sivilstand(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,
<<<<<<< HEAD
    val datoFom: LocalDate? = null,
    val datoTom: LocalDate? = null,
    val sivilstandstype: String,
=======
    val datoFom: Date?,
    val datoTom: Date?,
    @Enumerated(EnumType.STRING)
    val sivilstand: Sivilstandskode,
>>>>>>> main
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
