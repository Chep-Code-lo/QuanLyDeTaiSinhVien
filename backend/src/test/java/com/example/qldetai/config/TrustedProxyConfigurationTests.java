package com.example.qldetai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedProxyConfigurationTests {
    @Test
    void untrustedPeerCannotChangeAddressSchemeOrHostUsingForwardedHeaders() throws Exception {
        var filter = new TrustedProxyConfiguration.TrustedProxyFilter("10.0.0.10/32");
        var request = request("192.0.2.20");
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (incoming, response) -> {
                    var http = (jakarta.servlet.http.HttpServletRequest) incoming;
                    assertThat(http.getRemoteAddr()).isEqualTo("192.0.2.20");
                    assertThat(http.getScheme()).isEqualTo("http");
                    assertThat(http.getHeader("Forwarded")).isNull();
                    assertThat(http.getHeader("X-Forwarded-For")).isNull();
                });
    }

    @Test
    void explicitlyTrustedPeerCanForwardTheResolvedClientAddress() throws Exception {
        var filter = new TrustedProxyConfiguration.TrustedProxyFilter("10.0.0.10/32");
        var request = request("10.0.0.10");
        request.removeHeader("Forwarded");
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (incoming, response) -> {
                    assertThat(((jakarta.servlet.http.HttpServletRequest) incoming).getRemoteAddr())
                            .isEqualTo("203.0.113.42");
                    assertThat(incoming.getScheme()).isEqualTo("https");
                });
    }

    @Test
    void emptyOrUnavailableTrustConfigurationFailsClosed() throws Exception {
        for (String peers : new String[] {"", "unavailable-proxy.invalid"}) {
            var filter = new TrustedProxyConfiguration.TrustedProxyFilter(peers);
            filter.doFilter(
                    request("127.0.0.1"),
                    new MockHttpServletResponse(),
                    (incoming, response) ->
                            assertThat(
                                            ((jakarta.servlet.http.HttpServletRequest) incoming)
                                                    .getRemoteAddr())
                                    .isEqualTo("127.0.0.1"));
        }
    }

    private MockHttpServletRequest request(String peer) {
        var request = new MockHttpServletRequest("GET", "/api/probe");
        request.setRemoteAddr(peer);
        request.addHeader("Forwarded", "for=198.51.100.77;proto=https");
        request.addHeader("X-Forwarded-For", "203.0.113.42");
        request.addHeader("CF-Connecting-IP", "198.51.100.88");
        request.addHeader("X-Forwarded-Proto", "https");
        return request;
    }
}
