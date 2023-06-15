package no.nav.bidrag.behandling.controller

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import mu.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private val log = KotlinLogging.logger {}

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Suppress("unused")
class ExceptionHandler {
    @ResponseBody
    @ExceptionHandler(value = [MethodArgumentNotValidException::class, InvalidFormatException::class, IllegalArgumentException::class, MethodArgumentTypeMismatchException::class, ConversionFailedException::class, HttpMessageNotReadableException::class])
    fun handleInvalidValueExceptions(exception: Exception): ResponseEntity<*> {
        val cause = exception.cause ?: exception
        val validationError = when (cause) {
            is JsonMappingException -> createMissingKotlinParameterViolation(cause)
            is MethodArgumentNotValidException -> parseMethodARgumentNotValidexception(cause)
            else -> null
        }
        val errorMessage =
            validationError?.fieldErrors?.joinToString(", ") { "${it.field}: ${it.message}" }
                ?: "ukjent feil"
        log.warn(
            "Forespørselen inneholder ugyldig verdi: $errorMessage",
            exception,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                "Forespørselen inneholder ugyldig verdi: $errorMessage",
            )
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(HttpStatusCodeException::class)
    fun handleHttpClientErrorException(exception: HttpStatusCodeException): ResponseEntity<*> {
        val errorMessage = getErrorMessage(exception)
        log.warn(errorMessage, exception)
        return ResponseEntity
            .status(exception.statusCode)
            .header(HttpHeaders.WARNING, errorMessage)
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(Exception::class)
    fun handleOtherExceptions(exception: Exception): ResponseEntity<*> {
        log.warn("Det skjedde en ukjent feil", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(HttpHeaders.WARNING, "Det skjedde en ukjent feil: ${exception.message}")
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

    private fun createMissingKotlinParameterViolation(ex: JsonMappingException): Error {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        ex.path.forEach {
            error.addFieldError(it.from.toString(), it.fieldName, ex.message.toString())
        }
        return error
    }

    private fun parseMethodARgumentNotValidexception(ex: MethodArgumentNotValidException): Error {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        ex.fieldErrors.forEach {
            val message: String = if (it.defaultMessage == null) it.toString() else it.defaultMessage!!
            error.addFieldError(it.objectName, it.field, message)
        }
        return error
    }

    data class Error(val status: Int, val message: String, val fieldErrors: MutableList<CustomFieldError> = mutableListOf()) {
        fun addFieldError(objectName: String, field: String, message: String) {
            val error = CustomFieldError(objectName, field, message)
            fieldErrors.add(error)
        }
    }

    data class CustomFieldError(val objectName: String, val field: String, val message: String)
}
