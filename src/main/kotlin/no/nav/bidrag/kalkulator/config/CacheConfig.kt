package no.nav.bidrag.kalkulator.config

import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.bidrag.commons.cache.EnableUserCache
import no.nav.bidrag.commons.cache.InvaliderCacheFørStartenAvArbeidsdag
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableCaching
@Profile(value = ["!test"]) // Ignore cache on tests
@EnableUserCache
class CacheConfig {
    companion object {
        const val PERSON_CACHE = "PERSON_CACHE"
        const val FAMILIE_CACHE = "FAMILIE_CACHE"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.registerCustomCache(
            PERSON_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )
        caffeineCacheManager.registerCustomCache(
            FAMILIE_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build(),
        )

        return caffeineCacheManager
    }
}
