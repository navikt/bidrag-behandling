package no.nav.bidrag.behandling.dto.v1.forsendelse

import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoDto

fun BehandlingInfoDto.erBehandlingType(stonadType: Stønadstype?) = this.stonadType == stonadType

fun BehandlingInfoDto.erBehandlingType(engangsBelopType: Engangsbeløptype?) = this.engangsBelopType == engangsBelopType

fun BehandlingInfoDto.erGebyr() = erBehandlingType(Engangsbeløptype.GEBYR_SKYLDNER) || erBehandlingType(Engangsbeløptype.GEBYR_MOTTAKER)

fun BehandlingInfoDto.erBehandlingType(behandlingType: String?) = this.behandlingType == behandlingType

fun BehandlingInfoDto.erVedtakFattet() = erFattetBeregnet != null || vedtakId != null
