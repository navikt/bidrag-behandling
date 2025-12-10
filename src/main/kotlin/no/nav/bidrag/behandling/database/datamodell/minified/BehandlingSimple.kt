package no.nav.bidrag.behandling.database.datamodell.minified

import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype

data class BehandlingSimple(
    val id: Long,
    val stønadstype: Stønadstype?,
    val engangsbeløptype: Engangsbeløptype?,
    val forholdsmessigFordeling: ForholdsmessigFordeling?,
)
