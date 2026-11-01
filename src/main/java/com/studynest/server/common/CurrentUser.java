package com.studynest.server.common;

import com.studynest.server.common.exception.ApiException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import com.studynest.server.user.entity.UserStatus;
import com.studynest.server.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) { this.users = users; }

    public User get(Authentication auth) {
        if (auth == null) throw ApiException.forbidden();
        User user = users.findByEmail(auth.getName()).orElseThrow(ApiException::forbidden);
        if (user.getStatus() != UserStatus.ACTIVE) throw ApiException.forbidden();
        return user;
    }

    public User require(Authentication auth, UserRole role) {
        User user = get(auth);
        if (user.getRoleCode() != role) throw ApiException.forbidden();
        return user;
    }
}
