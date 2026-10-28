package com.example.ledger.common.error;

import com.example.ledger.common.error.ErrorResponse.FieldViolation;
import com.example.ledger.transaction.dto.TransactionResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE_CASE =
            new PropertyNamingStrategies.SnakeCaseStrategy();

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getCode(), ex.getMessage(), request, null, ex.getExistingTransaction());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .sorted(Comparator.comparing(FieldViolation::getField).thenComparing(FieldViolation::getMessage))
                .collect(Collectors.toList());
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, violations, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        Throwable cause = ex.getCause();
        String message = "Request body is missing or is not valid JSON";
        if (cause instanceof UnrecognizedPropertyException) {
            message = "Unknown or read-only field '" + ((UnrecognizedPropertyException) cause).getPropertyName()
                    + "' is not allowed in the request body";
        } else if (cause instanceof MismatchedInputException) {
            String field = jsonPath((JsonMappingException) cause);
            message = field.isEmpty()
                    ? "Request body has an invalid structure"
                    : "Field '" + field + "' has an invalid value or type";
        } else if (cause instanceof JsonParseException) {
            message = "Request body is not valid JSON";
        }
        return build(ErrorCode.INVALID_REQUEST_BODY, message, request, null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        String name = ex.getName();
        ErrorCode code = ("page".equals(name) || "limit".equals(name))
                ? ErrorCode.INVALID_PAGINATION_PARAMETERS
                : ErrorCode.INVALID_PARAMETER;
        return build(code, "Parameter '" + name + "' has an invalid value: '" + ex.getValue() + "'",
                request, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint", request, null, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", request, null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        String detail = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage())
                .toUpperCase(Locale.ROOT);
        log.warn("Database constraint rejected the request: {}", detail);
        if (detail.contains("UQ_TRANSACTIONS_ACCOUNT_REFERENCE")) {
            return build(ErrorCode.DUPLICATE_TRANSACTION,
                    "A transaction with this reference_id already exists for the account", request, null, null);
        }
        if (detail.contains("CHK_ACCOUNTS_BALANCE_NON_NEGATIVE")) {
            return build(ErrorCode.INSUFFICIENT_BALANCE, "Account has insufficient balance", request, null, null);
        }
        return build(ErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request violates a data integrity constraint", request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null, null);
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(SNAKE_CASE.translate(error.getField()), error.getDefaultMessage());
    }

    private static String jsonPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request,
                                                List<FieldViolation> details, TransactionResponse existing) {
        ErrorResponse body = new ErrorResponse(Instant.now(clock), code, message, request.getRequestURI(),
                details, existing);
        return ResponseEntity.status(code.status()).body(body);
    }
}
