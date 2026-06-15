package com.aitestforge.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("Business exception: {} - {}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(Map.of(
                "code", errorCode.getCode(),
                "message", errorCode.getMessage(),
                "status", errorCode.getHttpStatus().value()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation exception: {}", message);

        return ResponseEntity.badRequest().body(Map.of(
                "code", ErrorCode.INVALID_INPUT.getCode(),
                "message", message,
                "status", 400
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);

        return ResponseEntity.internalServerError().body(Map.of(
                "code", ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                "message", ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                "status", 500
        ));
    }
}
