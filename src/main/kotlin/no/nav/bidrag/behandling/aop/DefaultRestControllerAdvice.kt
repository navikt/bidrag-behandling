package no.nav.bidrag.behandling.aop

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@RestControllerAdvice
class DefaultRestControllerAdvice {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultRestControllerAdvice::class.java)
    }

    @ResponseBody
    @ExceptionHandler(value = [MethodArgumentNotValidException::class, IllegalArgumentException::class, MethodArgumentTypeMismatchException::class, ConversionFailedException::class, HttpMessageNotReadableException::class])
    fun handleInvalidValueExceptions(exception: Exception): ResponseEntity<*> {
        val cause = exception.cause
        val valideringsFeil =
            if (cause is MissingKotlinParameterException) {
                createMissingKotlinParameterViolation(
                    cause
                )
            } else {
                null
            }
        LOGGER.warn(
            "Forespørselen inneholder ugyldig verdi: ${valideringsFeil ?: "ukjent feil"}",
            exception
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                "Forespørselen inneholder ugyldig verdi: ${valideringsFeil ?: exception.message}"
            )
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(HttpStatusCodeException::class)
    fun handleHttpClientErrorException(exception: HttpStatusCodeException): ResponseEntity<*> {
        val errorMessage = getErrorMessage(exception)
        LOGGER.warn(errorMessage, exception)
        return ResponseEntity
            .status(exception.statusCode)
            .header(HttpHeaders.WARNING, errorMessage)
            .build<Any>()
    }

    private fun getErrorMessage(exception: HttpStatusCodeException): String {
        val errorMessage = StringBuilder()
        errorMessage.append("Det skjedde en feil ved kall mot ekstern tjeneste: ")
        exception.responseHeaders?.get(HttpHeaders.WARNING)?.firstOrNull()?.let { errorMessage.append(it) }
        if (exception.statusText.isNotEmpty()) {
            errorMessage.append(" - ")
            errorMessage.append(exception.statusText)
        }
        return errorMessage.toString()
    }

    @ResponseBody
    @ExceptionHandler(Exception::class)
    fun handleOtherExceptions(exception: Exception): ResponseEntity<*> {
        LOGGER.warn("Det skjedde en ukjent feil", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(HttpHeaders.WARNING, "Det skjedde en ukjent feil: ${exception.message}")
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(JwtTokenUnauthorizedException::class)
    fun handleUnauthorizedException(exception: JwtTokenUnauthorizedException): ResponseEntity<*> {
        LOGGER.warn("Ugyldig eller manglende sikkerhetstoken", exception)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WARNING, "Ugyldig eller manglende sikkerhetstoken")
            .build<Any>()
    }

    private fun createMissingKotlinParameterViolation(ex: MissingKotlinParameterException): String {
        val errorFieldRegex = Regex("\\.([^.]*)\\[\\\"(.*)\"\\]\$")
        val paths = ex.path.map { errorFieldRegex.find(it.description)!! }.map {
            val (objectName, field) = it.destructured
            "$objectName.$field"
        }
        return "${paths.joinToString("->")} kan ikke være null"
    }
}
