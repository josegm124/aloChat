package com.alochat.inbound.api;

import com.alochat.inbound.security.InboundAuthenticationException;
import com.alochat.inbound.validation.InboundValidationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InboundExceptionHandler {

    @ExceptionHandler(InboundValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(InboundValidationException exception) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(InboundAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(InboundAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(
                        Instant.now(),
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        exception.getMessage()
                )
        );
    }
}
