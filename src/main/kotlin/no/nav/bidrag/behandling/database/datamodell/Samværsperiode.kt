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
import no.nav.bidrag.transport.behandling.beregning.samvær.SamværskalkulatorDetaljer
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
    open var fom: LocalDate,
    open var tom: LocalDate?,
    @Enumerated(EnumType.STRING)
    open var samværsklasse: Samværsklasse,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @Column(name = "beregning", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var beregningJson: String? = null,
) {
    override fun toString(): String =
        "Samværsperiode(id=$id, datoFom=$fom, datoTom=$tom, samværsklasse=$samværsklasse, beregning=$beregningJson)"

    val beregning get() =
        try {
            beregningJson?.let { commonObjectmapper.readValue(beregningJson, SamværskalkulatorDetaljer::class.java) }
        } catch (e: Exception) {
            log.error {
                "Kunne ikke deserialisere samværskalkulator beregning for samvær ${samvær.id}:$id i behandling ${samvær.behandling.id}"
            }
            null
        }
}
