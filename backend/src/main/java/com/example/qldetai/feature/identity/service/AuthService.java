package com.example.qldetai.feature.identity.service;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LoginRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LoginResponse;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.security.UserPrincipal;
import com.example.qldetai.security.LoginAttemptLimiter;
import com.example.qldetai.security.DatabaseUserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import com.example.qldetai.feature.identity.validation.PasswordByteLength;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final LoginAttemptLimiter loginAttempts;
    private final DatabaseUserDetailsService userDetails;


    @Transactional
    public LoginResponse login(
            LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        Authentication authentication =
                loginAttempts.authenticate(request.username(), clientIp(httpRequest), () -> authenticateExistingPassword(request));
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, response);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = requireUser(principal.id());
        LocalDateTime now = LocalDateTime.now(clock);
        user.setLastLoginAt(now);
        user.setLastLoginIp(clientIp(httpRequest));
        return new LoginResponse(userProfileService.toCurrentUser(user));
    }

    private Authentication authenticateExistingPassword(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            // Read only the recorded algorithm identifier, never infer the original
            // password length from a BCrypt hash. PBKDF2 verifies the entire input.
            try {
                if (!userDetails.loadUserByUsername(request.username()).getPassword().startsWith("{pbkdf2}")) {
                    throw new BusinessRuleException(PasswordByteLength.MESSAGE);
                }
            } catch (UsernameNotFoundException ignored) {
                throw new BusinessRuleException(PasswordByteLength.MESSAGE);
            }
        }
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                request.username().trim(), request.password()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        return userProfileService.toCurrentUser(requireUser(userId));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Mật khẩu hiện tại không chính xác.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(
                    "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        UserPrincipal updated = UserPrincipal.from(user);
        // Publish the new credential snapshot only after the password update commits.
        // Other sessions still hold the old hash and are rejected by CurrentUserRefreshFilter.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        updated, null, updated.getAuthorities()));
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, httpRequest, response);
            }
        });
    }

    private User requireUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng."));
    }

    private String clientIp(HttpServletRequest request) {
        // ForwardedHeaderFilter has already resolved the client address and removed
        // X-Forwarded-For. X-Real-IP can still contain Nginx's Docker peer address.
        String address = request.getRemoteAddr();
        // Spring may represent a forwarded IPv6 address using URI brackets.
        if (address != null && address.startsWith("[") && address.endsWith("]")) {
            return address.substring(1, address.length() - 1);
        }
        return address;
    }
}
