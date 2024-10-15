package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
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
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDate

private val log = KotlinLogging.logger {}

@Entity
@JsonIgnoreProperties(value = ["samvær", "id"])
open class Samværsperiode(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "samvær_id", nullable = false)
    open var samvær: Samvær,
    open var datoFom: LocalDate?,
    open val datoTom: LocalDate?,
    @Enumerated(EnumType.STRING)
    open val samværsklasse: Samværsklasse,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @Column(name = "beregning", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var beregningJson: String? = null,
) {
    override fun toString(): String =
        "Samværsperiode(id=$id, datoFom=$datoFom, datoTom=$datoTom, samværsklasse=$samværsklasse, beregning=$beregningJson)"

    val beregning get() =
        try {
            commonObjectmapper.readValue(beregningJson, Samværskalkulator::class.java)
        } catch (e: Exception) {
            log.error {
                "Kunne ikke deserialisere samværskalkulator beregning for samvær ${samvær.id}:$id i behandling ${samvær.behandling.id}"
            }
            null
        }
}

data class Samværskalkulator(
    val ferier: List<SamværskalkulatorFerie> = emptyList(),
    val regelmessigSamværNetter: Int,
    val samværsklasse: Samværsklasse,
) {
    data class SamværskalkulatorFerie(
        val type: SamværskalkulatorFerietype,
        val bidragsmottaker: SamværskalkulatorPeriode,
        val bidragspliktig: SamværskalkulatorPeriode,
    )

    data class SamværskalkulatorPeriode(
        val antallNetter: Int = 0,
        val repetisjon: SamværskalkulatorPeriodeRepetisjon,
    )

    enum class SamværskalkulatorPeriodeRepetisjon {
        HVERT_ÅR,
        ANNEN_HVERT_ÅR,
    }

    enum class SamværskalkulatorFerietype {
        JUL_NYTTÅR,
        VINTERFERIE,
        PÅSKE,
        SOMMERFERIE,
        HØSTFERIE,
        ANNET,
    }
}
