package no.nav.bidrag.behandling.database.datamodell

import no.nav.bidrag.behandling.consumer.Grunnlag
import java.math.BigDecimal
import java.util.Date
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "inntekt")
data class Inntekt(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,

    val taMed: Boolean,
    val beskrivelse: String,
    @Suppress("NonAsciiCharacters")
    @Column(name = "BELOP")
    val beløp: BigDecimal,
    val datoTom: Date,
    val datoFom: Date,
    val ident: String,
    val fraGrunnlag: Boolean,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
