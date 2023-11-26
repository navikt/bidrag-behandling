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
import java.util.Date

@Entity(name = "barn_i_husstand_periode")
class HusstandsBarnPeriode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barn_i_husstand_id", nullable = false)
    val husstandsBarn: HusstandsBarn,
    val datoFom: Date?,
    val datoTom: Date?,
    @Enumerated(EnumType.STRING)
    val bostatus: Bostatuskode?,
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
