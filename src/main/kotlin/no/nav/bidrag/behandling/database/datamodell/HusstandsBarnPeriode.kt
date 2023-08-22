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
import java.util.Date

@Entity(name = "barn_i_husstand_periode")
data class HusstandsBarnPeriode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barn_i_husstand_id", nullable = false)
    val husstandsBarn: HusstandsBarn,

    val datoFom: Date?,

    val datoTom: Date?,

    @Enumerated(EnumType.STRING)
    val boStatus: BoStatusType?,

    val kilde: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
