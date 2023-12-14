package no.nav.bidrag.behandling.transformers

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.domene.enums.rolle.Rolletype

fun Rolle.tilReferanse() =
    when (rolletype) {
        Rolletype.BIDRAGSMOTTAKER -> "bidragsmottaker"
        Rolletype.BARN -> "søkandsbarn-$id"
        Rolletype.REELMOTTAKER -> "reelmottaker-$id"
        Rolletype.BIDRAGSPLIKTIG -> "bidragspliktig"
        else -> throw RuntimeException("Kunne ikke opprette referanse for rolle $rolletype")
    }
