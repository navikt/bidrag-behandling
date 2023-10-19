package no.nav.bidrag.behandling

import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles(value = ["test", "testcontainer"])
@DirtiesContext
class TestContainerRunner : SpringTestRunner() {
    companion object {
        @Container
        protected val postgreSqlDb =
            PostgreSQLContainer("postgres:14.5").apply {
                withDatabaseName("bidrag-behandling")
                withUsername("cloudsqliamuser")
                withPassword("admin")
                portBindings = listOf("7777:5432")
                start()
            }

        @Suppress("unused")
        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.jpa.database") { "POSTGRESQL" }
            registry.add("spring.datasource.type") { "com.zaxxer.hikari.HikariDataSource" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.locations") { "classpath:/db/migration" }
            registry.add("spring.datasource.url", postgreSqlDb::getJdbcUrl)
            registry.add("spring.datasource.password", postgreSqlDb::getPassword)
            registry.add("spring.datasource.username", postgreSqlDb::getUsername)
            registry.add("spring.datasource.hikari.connection-timeout") { 250 }
        }
    }
}
