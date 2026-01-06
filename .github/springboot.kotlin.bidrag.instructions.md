---
description: 'Guidelines for building Spring Boot base applications'
applyTo: '**/*.java, **/*.kt'
---

# Spring Boot Development

## General Instructions

- Make only high confidence suggestions when reviewing code changes.
- Write code with good maintainability practices, including comments on why certain design decisions were made.
- Handle edge cases and write clear exception handling.
- For libraries or external dependencies, mention their usage and purpose in comments.
- Write always in Kotlin

## Spring Boot Instructions

### Dependency Injection

- Use constructor injection for all required dependencies.
- Declare dependency fields as `private val` on the class defintion (Kotlin syntax).

### Configuration

- Use YAML files (`application.yml`) for externalized configuration.
- Environment Profiles: Use Spring profiles for different environments (dev, test, prod, q1, q2). Save environment variables under q1.yaml, q2.yaml, or prod.yaml files in the resources directory.
- Configuration Properties: Use @ConfigurationProperties for type-safe configuration binding
- Secrets Management: Externalize secrets using environment variables or secret management systems
- Unleash Features: Use Unleash for feature toggles. Configure UNLEASH_SERVER_API_URL, UNLEASH_SERVER_API_PROJECTS, UNLEASH_SERVER_API_TYPE, and UNLEASH_INSTANCE_ID in configuration files. Use UnleashFeaturesProvider for checking feature flags to enable/disable features dynamically.

### Code Organization

- Package Structure: Organize by feature/domain rather than by layer
- Separation of Concerns: Keep controllers thin, services focused, and repositories simple
- Utility Classes: Make utility classes final with private constructors

### Service Layer

- Place business logic in `@Service`-annotated classes.
- Services should be stateless and testable.
- Inject repositories via the constructor.
- Service method signatures should use domain IDs or DTOs, not expose repository entities directly unless necessary.

### Logging

- Use Kotlinlogger for all logging (`private val LOGGER = KotlinLogging.logger {}` over the class definition and `log.info { "Some log" }` for logging).
- Do not use concrete implementations (Logback, Log4j2) or `System.out.println()` directly.
- Use parameterized logging: `log.info { "Some log $variable1 ${variable2.test}" }`.

### Security & Input Handling

- Use parameterized queries | Always use Spring Data JPA or `NamedParameterJdbcTemplate` to prevent SQL injection.
- Validate request bodies and parameters using JSR-380 (`@NotNull`, `@Size`, etc.) annotations and `BindingResult`

### Performance Optimization

- To reduce loading time, use lazy initialization for heavy components where possible.
- Optimize database queries to avoid N+1 problems using fetch joins or batch fetching.
- Use asynchronous processing for long-running tasks to improve responsiveness.

### Rest Consumers

- Use `AbstractRestClient` from `no.nav.bidrag.commons.web.client.AbstractRestClient` for REST API consumers.
- Extend `AbstractRestClient` by passing the `RestOperations` template and a service name.
- Use methods like `postForEntity`, `getForEntity`, etc., for HTTP requests.
- Handle exceptions appropriately, such as `HttpStatusCodeException` for specific status codes (e.g., FORBIDDEN, NOT_FOUND).
- Apply `@Retryable` for resilience, with backoff strategies.
- Use `@BrukerCacheable` for caching responses where applicable.
- When adding a new consumer:
  - Add an environment variable for the service URL (e.g., `${BIDRAG_NEW_SERVICE_URL}`).
  - Add an environment variable for the service scope (e.g., `${BIDRAG_NEW_SERVICE_SCOPE}`).
  - Update `application.yaml` under `no.nav.security.jwt.client.registration` with a new entry for the service, including:
    - `resource-url`: `${BIDRAG_NEW_SERVICE_URL}`
    - `token-endpoint-url`: `https://login.microsoftonline.com/${AZURE_APP_TENANT_ID}/oauth2/v2.0/token`
    - `grant-type`: `urn:ietf:params:oauth:grant-type:jwt-bearer`
    - `scope`: `api://${BIDRAG_NEW_SERVICE_SCOPE}/.default`
    - `authentication`: client-id, client-secret, and client-auth-method as `client_secret_post`.
  - Update .nais YAML environments (e.g., q1.yaml, q2.yaml, prod.yaml) by adding the URL and scope environment variables.

### API Documentation

- Use SpringDoc OpenAPI (springdoc-openapi) for generating Swagger documentation.
- Configure `springdoc.packages-to-scan` to include controller packages (e.g., `no.nav.bidrag.behandling.controller`).
- Set `springdoc.paths-to-match` to filter API paths (e.g., `/api/**`).
- Customize Swagger UI with `springdoc.swagger-ui.path` (e.g., `/` for root access).
- Annotate REST endpoints with `@Operation` for summary and description.
- Use `@ApiResponse` for response codes and descriptions.
- Use `@Parameter` for path/query parameters.
- Use `@Schema` for request/response body schemas.
- Ensure all endpoints are documented for better API maintainability.

## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- Run `mvn clean test`.
- Ensure all tests pass as part of the build.

## Useful Commands

| Maven Command                                                                                                                                                                                  | Description                                   |
|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------|
| `mvn spring-boot:run`                                                                                                                                                                          | Run the application.                          |
| `mvn exec:java -Dexec.mainClass="no.nav.bidrag.dokument.produksjon.AppLocalKt" -Dexec.classpathScope=test -Dexec.args="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"` | Run the application locally (use this).       |
| `mvn package`                                                                                                                                                                                  | Build the application.                        |
| `mvn test`                                                                                                                                                                                     | Run tests.                                    |
| `mvn spring-boot:repackage`                                                                                                                                                                    | Package the application as a JAR.             |
| `mvnspring-boot:build-image`                                                                                                                                                                   | Package the application as a container image. |