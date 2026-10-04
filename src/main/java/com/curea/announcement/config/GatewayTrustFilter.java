package com.curea.announcement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayTrustFilter extends OncePerRequestFilter {

    public static final String TRUST_HEADER = "X-Gateway-Trust";

    private final byte[] secret;

    public GatewayTrustFilter(@Value("${gateway.trust-secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (secret.length > 0) {
            String given = request.getHeader(TRUST_HEADER);
            byte[] givenBytes = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(secret, givenBytes)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"UNTRUSTED_REQUEST\",\"description\":\"UNTRUSTED_REQUEST\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
