package com.example.qldetai.security;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.identity.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

@Component
public class CurrentUserRefreshFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final ObjectMapper objectMapper;

    public CurrentUserRefreshFilter(
            UserRepository userRepository,
            SecurityContextRepository securityContextRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal refreshed =
                userRepository
                        .findWithRolesById(principal.id())
                        .map(UserPrincipal::from)
                        .orElse(null);
        if (refreshed == null || !refreshed.isEnabled() || !refreshed.isAccountNonLocked()) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(), ApiResponse.failure("The account is not active."));
            return;
        }

        if (!Objects.equals(refreshed.password(), principal.password())) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.failure("Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại."));
            return;
        }

        if (!refreshed.equals(principal)) {
            Authentication updatedAuthentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            refreshed, null, refreshed.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(updatedAuthentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        filterChain.doFilter(request, response);
    }
}
