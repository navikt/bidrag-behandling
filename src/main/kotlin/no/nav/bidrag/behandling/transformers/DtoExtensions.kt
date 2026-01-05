package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.GebyrRolle
import no.nav.bidrag.behandling.database.datamodell.GebyrRolleSøknad
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettRolleDto
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.rolleManglerFødselsdato
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.boforhold.oppdaterePerioder
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate
import java.time.Period

fun Set<Sivilstand>.toSivilstandDto() =
    this.map { SivilstandDto(it.id, it.datoFom, it.datoTom, it.sivilstand, it.kilde) }.sortedBy { it.datoFom }.toSet()

fun Behandling.tilForsendelseRolleDto(saksnummer: String) =
    roller.filter { r -> !(r.rolletype == Rolletype.BARN && r.ident == null) }.filter { it.saksnummer == saksnummer }.map {
        no.nav.bidrag.behandling.dto.v1.forsendelse.ForsendelseRolleDto(
            fødselsnummer = Personident(it.ident!!),
            type = it.rolletype,
        )
    }

fun OpprettRolleDto.toRolle(behandling: Behandling): Rolle {
    val fødselsdatoPerson =
        fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
            ?: rolleManglerFødselsdato(rolletype)

    return Rolle(
        behandling = behandling,
        behandlingstema = behandlingstema,
        behandlingstatus = behandlingstatus,
        innkrevingstype = behandling.innkrevingstype,
        rolletype = rolletype,
        stønadstype = behandling.stonadstype,
        ident = ident?.verdi,
        fødselsdato = fødselsdatoPerson,
        navn = navn,
        beregnTil =
            if (behandling.vedtakstype == Vedtakstype.KLAGE) {
                BeregnTil.OPPRINNELIG_VEDTAKSTIDSPUNKT
            } else {
                BeregnTil.INNEVÆRENDE_MÅNED
            },
        opphørsdato = null,
        virkningstidspunkt =
            if (rolletype == Rolletype.BARN) {
                maxOf(fødselsdatoPerson.withDayOfMonth(1), behandling.virkningstidspunktEllerSøktFomDato)
            } else {
                null
            },
        årsak =
            if (rolletype == Rolletype.BARN) {
                behandling.årsak
            } else {
                null
            },
        avslag =
            if (rolletype == Rolletype.BARN) {
                behandling.avslag
            } else {
                null
            },
        innbetaltBeløp = innbetaltBeløp,
        harGebyrsøknad = harGebyrsøknad,
        gebyr =
            GebyrRolle(
                gebyrSøknader =
                    if (harGebyrsøknad) {
                        mutableSetOf(
                            GebyrRolleSøknad(
                                saksnummer = behandling.saksnummer,
                                søknadsid = behandling.soknadsid!!,
                                behandlingid = behandling.id,
                                referanse = referanseGebyr,
                            ),
                        )
                    } else {
                        mutableSetOf()
                    },
            ),
    )
}

fun OpprettRolleDto.toHusstandsmedlem(behandling: Behandling): Husstandsmedlem {
    val husstandsmedlem =
        Husstandsmedlem(
            behandling,
            Kilde.OFFENTLIG,
            ident = this.ident?.verdi,
            fødselsdato =
                this.fødselsdato ?: hentPersonFødselsdato(ident?.verdi)
                    ?: rolleManglerFødselsdato(rolletype),
        )

    husstandsmedlem.oppdaterePerioder(
        nyEllerOppdatertBostatusperiode =
            Bostatusperiode(
                husstandsmedlem = husstandsmedlem,
                bostatus = Bostatuskode.IKKE_MED_FORELDER,
                datoFom = behandling.eldsteVirkningstidspunkt,
                datoTom = null,
                kilde = Kilde.MANUELL,
            ),
    )

    return husstandsmedlem
}
