package no.nav.bidrag.behandling.controller.v1

import no.nav.bidrag.domene.enums.beregning.ResultatkodeBarnebidrag
import no.nav.bidrag.domene.enums.beregning.ResultatkodeForskudd
import no.nav.bidrag.domene.enums.beregning.ResultatkodeSærtilskudd
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.util.visningsnavn
import org.springframework.web.bind.annotation.GetMapping

@BehandlingRestController
class VisningsnavnController {
    @Suppress("unused")
    @GetMapping("/visningsnavn")
    fun hentVisningsnavn(): Map<String, String> {
        return Inntektsrapportering.entries.associate { it.name to it.visningsnavn.intern } +
            Bostatuskode.entries.associate { it.name to it.visningsnavn.intern } +
            Sivilstandskode.entries.associate { it.name to it.visningsnavn.intern } +
            ResultatkodeSærtilskudd.entries.associate { it.name to it.visningsnavn.intern } +
            ResultatkodeBarnebidrag.entries.associate { it.name to it.visningsnavn.intern } +
            ResultatkodeForskudd.entries.associate { it.name to it.visningsnavn.intern }
    }
}
