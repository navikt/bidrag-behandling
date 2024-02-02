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
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(name = "rolle")
@SQLDelete(sql = "UPDATE rolle SET deleted = true WHERE id=?")
@Where(clause = "deleted=false")
open class Rolle(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val rolletype: Rolletype,
    open val ident: String?,
    open val foedselsdato: LocalDate,
    open val opprettet: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    open val navn: String? = null,
    open val deleted: Boolean = false,
)

fun Rolle.tilPersonident() = ident?.let { Personident(it) }

fun Rolle.hentNavn() = navn ?: hentPersonVisningsnavn(ident) ?: ""
