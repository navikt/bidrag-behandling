package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.SequenceGenerator
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@Entity
open class Notat(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notat_id_seq")
    @SequenceGenerator(name = "notat_id_seq", sequenceName = "notat_id_seq", initialValue = 1, allocationSize = 1)
    open var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = false)
    open val rolle: Rolle,
    @Enumerated(EnumType.STRING)
    open val notattype: Notattype,
    open val innhold: String,
) {
    override fun toString(): String =
        "Notat(id=$id, behandlingsid=${behandling.id}, rolleid=${rolle.id}, notatttype=$notattype, innhold=$innhold"
}
