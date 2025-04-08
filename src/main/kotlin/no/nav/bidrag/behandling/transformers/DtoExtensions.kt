package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.rolleManglerFødselsdato
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate
import java.time.Period

fun Set<Sivilstand>.toSivilstandDto() =
    this.map { SivilstandDto(it.id, it.datoFom, it.datoTom, it.sivilstand, it.kilde) }.sortedBy { it.datoFom }.toSet()

fun Behandling.tilForsendelseRolleDto() =
    roller.filter { r -> !(r.rolletype == Rolletype.BARN && r.ident == null) }.map {
        no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto(
            fødselsnummer = Personident(it.ident!!),
            type = it.rolletype,
        )
    }

fun OpprettRolleDto.toRolle(behandling: Behandling): Rolle {
    val fødselsdatoKorrigert =
        fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
            ?: rolleManglerFødselsdato(rolletype)

    val barnErOver18 =
        Period
            .between(fødselsdatoKorrigert.plusYears(18).plusMonths(1), LocalDate.now().withDayOfMonth(1))
            .years >= 18
    return Rolle(
        behandling = behandling,
        rolletype = rolletype,
        ident = ident?.verdi,
        fødselsdato = fødselsdatoKorrigert,
        navn = navn,
        opphørsdato =
            if (barnErOver18) {
                fødselsdatoKorrigert.plusYears(18).plusMonths(1).withDayOfMonth(1)
            } else {
                null
            },
        innbetaltBeløp = innbetaltBeløp,
        harGebyrsøknad = harGebyrsøknad,
    )
}

fun OpprettRolleDto.toHusstandsmedlem(behandling: Behandling): Husstandsmedlem =
    Husstandsmedlem(
        behandling,
        Kilde.OFFENTLIG,
        ident = this.ident?.verdi,
        fødselsdato =
            this.fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
                ?: rolleManglerFødselsdato(rolletype),
    )
