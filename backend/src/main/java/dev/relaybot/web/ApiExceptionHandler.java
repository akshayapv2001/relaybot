package dev.relaybot.web;

import dev.relaybot.common.PermanentException;
import dev.relaybot.common.Redactor;
import dev.relaybot.common.RetryableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/** Consistent { "error": "..." } bodies for the dashboard. Messages are redacted before leaving the server. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", String.valueOf(e.getReason())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "The request was not valid."));
    }

    /** Discord or another upstream refused or was unreachable while serving a dashboard request. */
    @ExceptionHandler({PermanentException.class, RetryableException.class})
    ResponseEntity<Map<String, String>> upstream(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", Redactor.redact(e.getMessage())));
    }
}
