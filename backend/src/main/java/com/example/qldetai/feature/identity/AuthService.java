package com.example.qldetai.feature.identity;

import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.identity.IdentityDtos.ChangePasswordRequest;
import com.example.qldetai.feature.identity.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.IdentityDtos.LoginRequest;
import com.example.qldetai.feature.identity.IdentityDtos.LoginResponse;
import com.example.qldetai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final IdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            UserRepository userRepository,
            IdentityMapper identityMapper,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim(), request.password()));
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, response);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = requireUser(principal.id());
        user.setLastLoginAt(LocalDateTime.now(clock));
        user.setUpdatedAt(LocalDateTime.now(clock));
        return new LoginResponse(identityMapper.toCurrentUser(user));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        return identityMapper.toCurrentUser(requireUser(userId));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must differ from the current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now(clock));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }
}
