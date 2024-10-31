package no.nav.bidrag.behandling.transformers.person

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident

fun Person.tilPersoninfoDto(behandling: Behandling) =
    PersoninfoDto(
        id = this.id,
        ident = this.ident?.let { Personident(it) },
        navn = this.navn,
        fødselsdato = this.fødselsdato,
        kilde = if (this.rolle.size > 0) Kilde.OFFENTLIG else Kilde.MANUELL,
        medIBehandlingen = this.harRolleIBehandling(behandling),
    )

fun Person.harRolleIBehandling(behandling: Behandling) =
    this.ident?.let { ident ->
        behandling.roller.find { it.ident == ident } != null
    } ?: false
