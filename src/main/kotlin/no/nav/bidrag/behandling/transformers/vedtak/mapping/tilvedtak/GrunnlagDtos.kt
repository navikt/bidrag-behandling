@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.transport.behandling.felles.grunnlag.BaseGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettEngangsbeløpRequestDto
import java.math.BigDecimal

internal data class GebyrResulat(
    val engangsbeløp: List<OpprettEngangsbeløpRequestDto>,
    val grunnlagsliste: Set<BaseGrunnlag>,
)

data class BeregnGebyrResultat(
    val skattepliktigInntekt: BigDecimal,
    val maksBarnetillegg: BigDecimal?,
    val ilagtGebyr: Boolean,
    val beløpGebyrsats: BigDecimal,
    val resultatkode: Resultatkode,
    val grunnlagsreferanseListeEngangsbeløp: List<Grunnlagsreferanse>,
    val grunnlagsliste: List<BaseGrunnlag>,
)
