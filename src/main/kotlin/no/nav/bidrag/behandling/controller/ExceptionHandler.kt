package no.nav.bidrag.behandling.controller

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.Locale

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Suppress("unused")
class ExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun methodArgumentNotValidException(ex: MethodArgumentNotValidException): Error? {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        ex.fieldErrors.forEach {
            val message: String = if (it.defaultMessage == null) it.toString() else it.defaultMessage!!
            error.addFieldError(it.objectName, it.field, message)
        }
        return error
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(MissingKotlinParameterException::class)
    fun missingKotlinParameterException(ex: MissingKotlinParameterException): Error? {
        return createMissingKotlinParameterViolation(ex)
    }

    private fun createMissingKotlinParameterViolation(ex: MissingKotlinParameterException): Error {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        val errorFieldRegex = Regex("\\.([^.]*)\\[\\\"(.*)\"\\]\$")
        val errorMatch = errorFieldRegex.find(ex.path[0].description)!!
        val (objectName, field) = errorMatch.destructured
        error.addFieldError(objectName.replaceFirstChar { it.lowercase(Locale.getDefault()) }, field, "must not be null")
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
