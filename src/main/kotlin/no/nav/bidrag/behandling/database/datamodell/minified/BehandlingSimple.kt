package no.nav.bidrag.behandling.database.datamodell.minified

import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordeling
import no.nav.bidrag.behandling.database.datamodell.json.Omgjøringsdetaljer
import no.nav.bidrag.behandling.dto.v1.behandling.OppdatereVirkningstidspunkt
import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.regnskap.Søknadstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import java.time.LocalDate

data class BehandlingSimple(
    val id: Long,
    val virkningstidspunkt: LocalDate?,
    val søktFomDato: LocalDate,
    val mottattdato: LocalDate,
    val saksnummer: String,
    val harPrivatAvtaleAndreBarn: Boolean,
    val vedtakstype: Vedtakstype?,
    val søknadstype: Behandlingstype?,
    val omgjøringsdetaljer: Omgjøringsdetaljer?,
    val stønadstype: Stønadstype?,
    val engangsbeløptype: Engangsbeløptype?,
    val forholdsmessigFordeling: ForholdsmessigFordeling?,
    val roller: List<RolleSimple> = emptyList(),
) {
    constructor(
        id: Long,
        virkningstidspunkt: LocalDate,
        søktFomDato: LocalDate,
        mottattdato: LocalDate,
        saksnummer: String,
        vedtakstype: Vedtakstype?,
        søknadstype: Behandlingstype?,
        omgjøringsdetaljer: Omgjøringsdetaljer?,
        stønadstype: Stønadstype?,
        engangsbeløptype: Engangsbeløptype?,
        forholdsmessigFordeling: ForholdsmessigFordeling?,
    ) : this(
        id,
        virkningstidspunkt,
        søktFomDato,
        mottattdato,
        saksnummer,
        false,
        vedtakstype,
        søknadstype,
        omgjøringsdetaljer,
        stønadstype,
        engangsbeløptype,
        forholdsmessigFordeling,
        emptyList(),
    )

    val søknadsbarn get() = roller.filter { it.rolletype == Rolletype.BARN }
    val bidragspliktig get() = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
    val erKlageEllerOmgjøring get() = vedtakstype == Vedtakstype.KLAGE || omgjøringsdetaljer?.opprinneligVedtakId != null
}

data class RolleSimple(
    val rolletype: Rolletype,
    val ident: String,
    val virkningstidspunkt: LocalDate?,
) {
    val personident get() = Personident(ident)
}
