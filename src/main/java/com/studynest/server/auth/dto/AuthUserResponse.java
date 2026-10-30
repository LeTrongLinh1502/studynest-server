package com.studynest.server.auth.dto;

import com.studynest.server.user.entity.User;

public record AuthUserResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRoleCode().name()
        );
    }
}
