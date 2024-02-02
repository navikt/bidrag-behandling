package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.manglerRolle
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import java.time.LocalDate

fun Behandling.tilBeregnGrunnlagBarn(søknadsbarnRolle: Rolle): BeregnGrunnlag {
    val bm = bidragsmottaker?.tilPersonGrunnlag() ?: manglerRolle(Rolletype.BIDRAGSMOTTAKER, id!!)
    val søknadsbarn = søknadsbarnRolle.tilPersonGrunnlag()
    val øvrigeBarnIHusstand = oppretteGrunnlagForHusstandsbarn(søknadsbarnRolle.ident)
    val personobjekterBarn = listOf(søknadsbarn) + øvrigeBarnIHusstand
    val bostatusBarn = this.tilGrunnlagBostatus(personobjekterBarn.toSet())
    val inntektBm = this.tilGrunnlagInntekt(bm, søknadsbarn)
    val sivilstandBm = this.tilGrunnlagSivilstand(bm)

    val inntekterBarn = this.tilGrunnlagInntekt(søknadsbarn)
    return BeregnGrunnlag(
        periode =
            ÅrMånedsperiode(
                this.virkningstidspunkt!!,
                this.datoTom?.plusDays(1) ?: LocalDate.MAX,
            ),
        søknadsbarnReferanse = søknadsbarn.referanse,
        grunnlagListe =
            personobjekterBarn + bostatusBarn + inntektBm + sivilstandBm + inntekterBarn + bm,
    )
}
