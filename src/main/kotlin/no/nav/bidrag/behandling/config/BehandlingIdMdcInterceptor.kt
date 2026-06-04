package no.nav.bidrag.behandling.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.servlet.AsyncHandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class BehandlingIdMdcInterceptor(
    private val behandlingRepository: BehandlingRepository,
) : AsyncHandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val behandlingId = request.hentBehandlingIdFraPathVariable()
        if (behandlingId == null) {
            fjernMdcVerdier()
        } else {
            MDC.put(MDC_BEHANDLING_ID, behandlingId)
            oppdaterSaksnummerIMdc(behandlingId)
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        fjernMdcVerdier()
    }

    override fun afterConcurrentHandlingStarted(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ) {
        fjernMdcVerdier()
    }

    private fun oppdaterSaksnummerIMdc(behandlingId: String) {
        val saksnummer =
            behandlingId.toLongOrNull()?.let {
                runCatching { behandlingRepository.hentSaksnummer(it) }.getOrNull()
            }

        if (saksnummer.isNullOrBlank()) {
            MDC.remove(MDC_SAKSNUMMER)
        } else {
            MDC.put(MDC_SAKSNUMMER, saksnummer)
        }
    }

    private fun fjernMdcVerdier() {
        MDC.remove(MDC_BEHANDLING_ID)
        MDC.remove(MDC_SAKSNUMMER)
    }

    private fun HttpServletRequest.hentBehandlingIdFraPathVariable(): String? {
        val pathVariabler =
            getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
                as? Map<*, *>
                ?: return null

        return pathVariabler.entries
            .firstOrNull { it.key.toString().lowercase() in BEHANDLING_ID_PATH_VARIABELNAVN }
            ?.value
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val MDC_BEHANDLING_ID = "behandlingId"
        const val MDC_SAKSNUMMER = "saksnummer"
        private val BEHANDLING_ID_PATH_VARIABELNAVN = setOf("behandlingid", "behandlingsid")
    }
}
