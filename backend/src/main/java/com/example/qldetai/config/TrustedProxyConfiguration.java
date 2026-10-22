package com.example.qldetai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class TrustedProxyConfiguration {
    @Bean
    FilterRegistrationBean<TrustedProxyFilter> trustedProxyFilter(
            @Value("${app.security.trusted-proxies:}") String proxies) {
        var registration = new FilterRegistrationBean<>(new TrustedProxyFilter(proxies));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static final class TrustedProxyFilter extends OncePerRequestFilter {
        private final List<String> proxies;
        private final ForwardedHeaderFilter trusted = new ForwardedHeaderFilter();
        private final ForwardedHeaderFilter untrusted = new ForwardedHeaderFilter();

        TrustedProxyFilter(String configured) {
            proxies = Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
            untrusted.setRemoveOnly(true);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            (isTrusted(request.getRemoteAddr()) ? trusted : untrusted).doFilter(request, response, chain);
        }

        private boolean isTrusted(String peer) {
            if (peer == null) return false;
            for (String proxy : proxies) {
                if (proxy.contains("/")) {
                    if (new IpAddressMatcher(proxy).matches(peer)) return true;
                } else {
                    // Compose service names let the explicit Nginx peer survive container recreation.
                    // DNS failure is fail-closed; incoming headers never determine this identity.
                    try {
                        for (InetAddress address : InetAddress.getAllByName(proxy)) {
                            if (address.equals(InetAddress.getByName(peer))) return true;
                        }
                    } catch (UnknownHostException ignored) {
                        // An unavailable configured proxy cannot grant forwarded-header trust.
                    }
                }
            }
            return false;
        }
    }
}
