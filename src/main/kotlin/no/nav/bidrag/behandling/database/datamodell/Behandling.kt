package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany

@Entity(name = "behandling")
data class Behandling(
    @Enumerated(EnumType.STRING)
    val behandlingType: BehandlingType,

    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,

    val datoFom: Date,

    val datoTom: Date,

    val mottatDato: Date,

    val saksnummer: String,

    val behandlerEnhet: String,

    val soknadFra: SoknadFraType,

    val virkningsDato: Date? = null,

    @Enumerated(EnumType.STRING)
    val aarsak: ForskuddBeregningKodeAarsakType? = null,

    val avslag: String? = null,

    @Column(name = "BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    val begrunnelseMedIVedtakNotat: String? = null,

    @Column(name = "BEGRUNNELSE_KUN_I_NOTAT")
    val begrunnelseKunINotat: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) {
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    val roller: MutableSet<Rolle> = mutableSetOf()
}
