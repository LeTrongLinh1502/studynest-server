package com.studynest.server.common.exception;

import com.studynest.server.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse response = ErrorResponse.withFields(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request data is invalid",
                fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "EMAIL_ALREADY_EXISTS",
                exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials() {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_CREDENTIALS",
                "Email or password is incorrect"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount() {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_LOCKED",
                "Account is locked"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount() {
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_DISABLED",
                "Account is disabled"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}
