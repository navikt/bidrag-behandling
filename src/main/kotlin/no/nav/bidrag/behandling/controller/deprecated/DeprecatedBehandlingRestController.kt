package no.nav.bidrag.behandling.controller.deprecated

import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.annotation.Inherited

@Deprecated("Bruk BehandlingRestController v1 i stedet")
@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@RestController
@Protected
@RequestMapping("/api")
annotation class DeprecatedBehandlingRestController
