package no.nav.bidrag.behandling.controller

import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.util.visningsnavn
import org.springframework.web.bind.annotation.GetMapping

@BehandlingRestControllerV1
class VisningsnavnController {
    @Suppress("unused")
    @GetMapping("/visningsnavn")
    fun hentVisningsnavn(): Map<String, String> {
        return Inntektsrapportering.entries.associate { it.name to it.visningsnavn.intern } +
            VirkningstidspunktÅrsakstype.entries.associate { it.name to it.visningsnavn.intern } +
            Resultatkode.entries.associate { it.name to it.visningsnavn.intern } +
            Bostatuskode.entries.associate { it.name to it.visningsnavn.intern } +
            Sivilstandskode.entries.associate { it.name to it.visningsnavn.intern }
    }
}
