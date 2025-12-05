package no.nav.bidrag.behandling.config

import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.bidrag.commons.cache.EnableUserCache
import no.nav.bidrag.commons.cache.InvaliderCacheFørStartenAvArbeidsdag
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
@Profile(value = ["!test"]) // Ignore cache on tests
@EnableUserCache
class CacheConfig {
    companion object {
        const val SAMHANDLER_CACHE = "SAMHANDLER_CACHE"
        const val PERSON_CACHE = "PERSON_CACHE"
        const val PERSON_FØDSELSDATO_CACHE = "PERSON_FØDSELSDATO_CACHE"
        const val PERSON_RELASJON_CACHE = "PERSON_RELASJON_CACHE"
        const val STØNAD_HISTORIKK_CACHE_2 = "STØNAD_HISTORIKK_CACHE_2"
        const val STØNAD_HISTORIKK_CACHE = "STØNAD_HISTORIKK_CACHE"
        const val BBM_BEREGNING_CACHE = "BBM_BEREGNING_CACHE"
        const val BBM_ALLE_BEREGNINGER_CACHE = "BBM_ALLE_BEREGNINGER_CACHE"
        const val VEDTAK_FOR_STØNAD_CACHE = "VEDTAK_FOR_STØNAD_CACHE"
        const val VEDTAK_CACHE = "VEDTAK_CACHE"
        const val STØNAD_LØPENDE_BIDRAG_CACHE = "STØNAD_LØPENDE_BIDRAG_CACHE"

        const val HENT_ALLE_STØNADER_CACHE = "hentAlleStønaderForBidragspliktig"
        const val SAK_CACHE = "SAK_CACHE"
        const val SAK_PERSON_CACHE = "SAK_PERSON_CACHE"
        const val TILGANG_TEMA_CACHE = "TILGANG_TEMA_CACHE"
        const val TILGANG_PERSON_I_SAK_CACHE = "TILGANG_SAK_CACHE"
        const val PERSON_HAR_BESKYTTELSE = "PERSON_HAR_BESKYTTELSE"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.registerCustomCache(
            PERSON_FØDSELSDATO_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            PERSON_RELASJON_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            SAMHANDLER_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            PERSON_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            STØNAD_HISTORIKK_CACHE,
            Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            STØNAD_HISTORIKK_CACHE_2,
            Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(TILGANG_TEMA_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(BBM_BEREGNING_CACHE, Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build())
        caffeineCacheManager.registerCustomCache(
            BBM_ALLE_BEREGNINGER_CACHE,
            Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            STØNAD_LØPENDE_BIDRAG_CACHE,
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            VEDTAK_FOR_STØNAD_CACHE,
            Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            TILGANG_PERSON_I_SAK_CACHE,
            Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            SAK_CACHE,
            Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            SAK_PERSON_CACHE,
            Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            PERSON_HAR_BESKYTTELSE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            VEDTAK_CACHE,
            Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build(),
        )
        caffeineCacheManager.registerCustomCache(
            HENT_ALLE_STØNADER_CACHE,
            Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build(),
        )

        return caffeineCacheManager
    }
}
