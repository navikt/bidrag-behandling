package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.media.Schema
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
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDateTime

@Entity(name = "grunnlag")
@Schema(name = "GrunnlagEntity")
open class Grunnlag(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val type: Grunnlagsdatatype,
    @Column(name = "data", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open val data: String,
    open val innhentet: LocalDateTime,
    open var aktiv: LocalDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = false)
    open val rolle: Rolle,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
)

inline fun <reified T> Grunnlag?.hentData(): T? = konverterData<T>() as T

val List<Grunnlag>.inntekt: Grunnlag?
    get() = find { it.type == Grunnlagsdatatype.INNTEKT }
val List<Grunnlag>.sivilstand: Grunnlag?
    get() = find { it.type == Grunnlagsdatatype.SIVILSTAND }
val List<Grunnlag>.husstandmedlemmer: Grunnlag?
    get() = find { it.type == Grunnlagsdatatype.HUSSTANDSMEDLEMMER }
val List<Grunnlag>.arbeidsforhold: Grunnlag?
    get() = find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }

inline fun <reified T> Grunnlag?.konverterData(): T? = this?.data?.let { commonObjectmapper.readValue(it) }
