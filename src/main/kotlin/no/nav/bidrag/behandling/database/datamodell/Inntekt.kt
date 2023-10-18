package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.math.BigDecimal
import java.util.Date

@Entity(name = "inntekt")
class Inntekt(
    val inntektType: String?,
    val belop: BigDecimal,
    val datoFom: Date?,
    val datoTom: Date?,
    val ident: String,
    val fraGrunnlag: Boolean,
    val taMed: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling? = null,
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "inntekt", cascade = [CascadeType.ALL], orphanRemoval = true)
    var inntektPostListe: MutableSet<InntektPostDomain> = mutableSetOf(),
)
