/*
package no.nav.bidrag.kalkulator

import org.slf4j.LoggerFactory
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles(value = ["test", "testcontainer"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TestContainerRunner : SpringTestRunner() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TestContainerRunner::class.java)

        @JvmStatic
        @Container
        protected val postgreSqlDb =
            PostgreSQLContainer("postgres:15.4").apply {
                withDatabaseName("bidrag-behandling")
                withUsername("cloudsqliamuser")
                withPassword("admin")
                withInitScript("db/init.sql")
//                withLogConsumer(Slf4jLogConsumer(LOGGER))
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
        }
    }
}
*/
