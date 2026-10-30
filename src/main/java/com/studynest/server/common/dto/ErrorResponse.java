package com.studynest.server.common.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(Instant.now(), status, code, message, Map.of());
    }

    public static ErrorResponse withFields(
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        return new ErrorResponse(Instant.now(), status, code, message, fieldErrors);
    }
}
