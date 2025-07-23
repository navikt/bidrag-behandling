package no.nav.bidrag.behandling.config

import io.getunleash.variant.Variant
import no.nav.bidrag.commons.unleash.UnleashFeaturesProvider

enum class UnleashFeatures(
    val featureName: String,
    defaultValue: Boolean,
) {
    BIDRAG_V2_ENDRING("behandling.v2_endring", false),
    BEGRENSET_REVURDERING("behandling.begrenset_revurdering", false),
    VEDTAKSSPERRE("vedtakssperre", false),
    ;

    private var defaultValue = false

    init {
        this.defaultValue = defaultValue
    }

    val isEnabled: Boolean
        get() = UnleashFeaturesProvider.isEnabled(feature = featureName, defaultValue = defaultValue)

    val variant: Variant?
        get() = UnleashFeaturesProvider.getVariant(featureName)
}
