package no.nav.bidrag.behandling.controller

import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.inntekt.Inntektstype
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.privatavtale.PrivatAvtaleType
import no.nav.bidrag.domene.enums.samhandler.Valutakode
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorFerietype
import no.nav.bidrag.domene.enums.samværskalkulator.SamværskalkulatorNetterFrekvens
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.util.visningsnavn
import org.springframework.web.bind.annotation.GetMapping

@BehandlingRestControllerV1
class VisningsnavnController {
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("unused")
    @GetMapping("/visningsnavn")
    fun hentVisningsnavn(): Map<String, String> =
        Inntektsrapportering.entries.associate { it.name to it.visningsnavn.intern } +
            VirkningstidspunktÅrsakstype.entries.associate { it.name to it.visningsnavn.intern } +
            Inntektstype.entries.associate { it.name to it.visningsnavn.intern } +
            Vedtakstype.entries.associate { it.name to it.visningsnavn.intern } +
            Resultatkode.entries.associate { it.name to it.visningsnavn.intern } +
            Bostatuskode.entries.associate { it.name to it.visningsnavn.intern } +
            Sivilstandskode.entries.associate { it.name to it.visningsnavn.intern } +
            Særbidragskategori.entries.associate { it.name to it.visningsnavn.intern } +
            Samværsklasse.entries.associate { it.name to it.visningsnavn.intern } +
            SamværskalkulatorFerietype.entries.associate { it.name to it.visningsnavn.intern } +
            SamværskalkulatorNetterFrekvens.entries.associate { it.name to it.visningsnavn.intern } +
            Engangsbeløptype.entries.associate { it.name to it.visningsnavn.intern } +
            Utgiftstype.entries.associate { it.name to it.visningsnavn.intern } +
            PrivatAvtaleType.entries.associate { it.name to it.visningsnavn.intern } +
            BeregnTil.entries.associate { it.name to it.visningsnavn.intern } +
            Behandlingstype.entries.associate { it.name to it.visningsnavn.intern } +
            Valutakode.entries.associate { it.name to it.visningsnavn }
}
