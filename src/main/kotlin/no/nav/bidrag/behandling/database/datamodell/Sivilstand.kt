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
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.time.LocalDate

@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Entity(name = "sivilstand")
open class Sivilstand(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    open val datoFom: LocalDate? = null,
    open val datoTom: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    open val sivilstand: Sivilstandskode,
    @Enumerated(EnumType.STRING)
    open val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
)
