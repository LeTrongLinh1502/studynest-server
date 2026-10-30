package com.studynest.server.auth.dto;

public record CsrfResponse(String token, String headerName) {
}
