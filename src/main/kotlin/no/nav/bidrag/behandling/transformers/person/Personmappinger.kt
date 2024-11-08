package no.nav.bidrag.behandling.transformers.person

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Person
import no.nav.bidrag.behandling.dto.v2.behandling.PersoninfoDto
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.ident.Personident

fun Person.tilPersoninfoDto(behandling: Behandling): PersoninfoDto {
    val rolle =
        behandling.roller.find { r ->
            this.rolle
                .map { it.id }
                .toSet()
                .contains(r.id)
        }

    return PersoninfoDto(
        id = this.id,
        ident = rolle?.ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) },
        navn = this.navn,
        fødselsdato = rolle?.fødselsdato ?: this.fødselsdato,
        kilde = rolle?.ident?.let { Kilde.OFFENTLIG } ?: Kilde.MANUELL,
        medIBehandlingen = rolle?.ident != null,
    )
}
