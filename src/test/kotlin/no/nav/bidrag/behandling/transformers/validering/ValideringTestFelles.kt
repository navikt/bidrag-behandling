package no.nav.bidrag.behandling.transformers.validering

import no.nav.bidrag.behandling.database.datamodell.Bostatusperiode
import no.nav.bidrag.behandling.database.datamodell.Husstandsmedlem
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.utils.testdata.oppretteBehandling
import no.nav.bidrag.behandling.utils.testdata.oppretteHusstandsmedlem
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.tid.Datoperiode
import java.time.LocalDate
import kotlin.random.Random

val bmIdent = "313213213"
val bpIdent = "5533214"
val barnIdent = "1344124"
val barn2Ident = "44444"
val husstandsmedlem1Ident = "3334444"
val husstandsmedlem2Ident = "333455444"

fun opprettSivilstand(perioder: List<Pair<Datoperiode, Sivilstandskode>>): MutableSet<Sivilstand> =
    perioder
        .map {
            Sivilstand(
                behandling = oppretteBehandling(),
                kilde = Kilde.MANUELL,
                id = Random.nextLong(1000),
                sivilstand = it.second,
                datoTom = it.first.til,
                datoFom = it.first.fom,
            )
        }.toMutableSet()

fun opprettHusstandsmedlem(
    perioder: List<Pair<Datoperiode, Bostatuskode?>>,
    ident: String? = null,
    fødselsdato: LocalDate = LocalDate.parse("2022-01-01"),
    rolle: Rolle? = null,
): Husstandsmedlem =
    Husstandsmedlem(
        behandling = oppretteBehandling(),
        kilde = Kilde.MANUELL,
        id = Random.nextLong(1000),
        ident = ident,
        navn = ident,
        fødselsdato = fødselsdato,
        rolle = rolle,
        perioder =
            perioder
                .map {
                    Bostatusperiode(
                        husstandsmedlem = oppretteHusstandsmedlem(oppretteBehandling(), testdataBP),
                        datoFom = it.first.fom,
                        datoTom = it.first.til,
                        bostatus = it.second ?: Bostatuskode.DELT_BOSTED,
                        kilde = Kilde.MANUELL,
                        id = Random.nextLong(1000),
                    )
                }.toMutableSet(),
    )
