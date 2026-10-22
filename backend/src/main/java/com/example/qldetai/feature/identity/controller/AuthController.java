package com.example.qldetai.feature.identity.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LoginRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LoginResponse;
import com.example.qldetai.feature.identity.service.AuthService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.success(
                Map.of(
                        "headerName", token.getHeaderName(),
                        "parameterName", token.getParameterName(),
                        "token", token.getToken()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return ApiResponse.success(
                "Đăng nhập thành công.", authService.login(request, httpRequest, response));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.id()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        authService.changePassword(principal.id(), request, httpRequest, response);
        return ApiResponse.success("Đổi mật khẩu thành công.", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        return ApiResponse.success("Đăng xuất thành công.", null);
    }
}
