package no.nav.bidrag.behandling.config

import io.getunleash.variant.Variant
import no.nav.bidrag.commons.unleash.UnleashFeaturesProvider

enum class UnleashFeatures(
    val featureName: String,
    defaultValue: Boolean,
) {
    DEBUG_LOGGING("debug_logging", false),

    // I Q1 ved opprettelse av klage så blir alle inntekter fjernet fordi de ikke finnes i testmiljøene.
    // Dette er for å unngå de slettes ved grunnlagsinnhenting
    GRUNNLAGSINNHENTING_FUNKSJONELL_FEIL_TEKNISK("behandling.grunnlag_behandle_funksjonell_feil_som_teknisk", false),
    FATTE_VEDTAK("behandling.fattevedtak_klage", false),
    BIDRAG_KLAGE("behandling.bidrag_klage", false),
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
