package com.studynest.server.auth.service;

import com.studynest.server.auth.dto.AuthUserResponse;
import com.studynest.server.auth.dto.SignupRequest;
import com.studynest.server.common.exception.EmailAlreadyExistsException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthUserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        String fullName = normalizeFullName(request.fullName());
        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.createStudent(fullName, email, passwordHash);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return AuthUserResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return AuthUserResponse.from(user);
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
