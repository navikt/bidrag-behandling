package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.sak.RolleDto

val skyldnerNav = Personident("NAV")

// TODO: Reel mottaker fra bidrag-sak?
fun Set<Rolle>.reelMottakerEllerBidragsmottaker(rolle: RolleDto) =
    rolle.reellMottager?.personIdent()
        ?: find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }!!.let { Personident(it.ident!!) }

fun <T, R> T?.takeIfNotNullOrEmpty(block: (T) -> R): R? {
    return if (this == null || this is String && this.trim().isEmpty()) null else block(this)
}
