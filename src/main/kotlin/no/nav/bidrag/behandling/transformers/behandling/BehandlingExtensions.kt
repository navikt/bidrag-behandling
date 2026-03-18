package no.nav.bidrag.behandling.transformers.behandling

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.transformers.dato18ÅrsBidrag
import no.nav.bidrag.behandling.transformers.erBidrag
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.beregning.felles.ÅpenSøknadDto
import java.time.LocalDate

fun Behandling.finnRolleForPeriode(
    ident: String,
    stønadstype: Stønadstype? = null,
    periodeFom: LocalDate,
): Rolle? {
    val stønadstypeBeregnet =
        if (stønadstype != null || !this.erBidrag()) {
            stønadstype
        } else if (roller.count { it.ident == ident } == 1) {
            roller.find { it.ident == ident }?.stønadstype
        } else {
            val dato18ÅrsBidrag = hentPersonFødselsdato(ident)?.dato18ÅrsBidrag
            if (dato18ÅrsBidrag != null && dato18ÅrsBidrag >= periodeFom) {
                Stønadstype.BIDRAG18AAR
            } else {
                Stønadstype.BIDRAG
            }
        }
    return roller.find {
        it.erSammeRolle(ident, stønadstypeBeregnet)
    }
}

fun Behandling.finnRolle(
    ident: String,
    stønadstype: Stønadstype? = null,
) = roller.find {
    it.erSammeRolle(ident, stønadstype)
}

fun Pair<String?, Stønadstype?>.erSamme(
    ident: String,
    stønadstype: Stønadstype?,
) = erSammePerson(ident, stønadstype, first, second)

fun List<Pair<String?, Stønadstype?>>.finnes(
    ident: String,
    stønadstype: Stønadstype?,
) = any {
    it.erSamme(ident, stønadstype)
}

fun erSammePerson(
    ident1: String?,
    stønadstype1: Stønadstype?,
    ident2: String?,
    stønadstype2: Stønadstype?,
) = ident1 == ident2 &&
    (stønadstype1 == null || stønadstype2 == null || stønadstype1 == stønadstype2)
