package com.v_disk.config;

import java.io.IOException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.v_disk.service.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityLoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(SecurityLoggingAuthenticationEntryPoint.class);

    private final JwtService jwtService;

    public SecurityLoggingAuthenticationEntryPoint(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException)
            throws IOException, ServletException {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String remoteAddr = request.getRemoteAddr();
        String authHeader = request.getHeader("Authorization");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principalInfo = (auth != null) ? String.valueOf(auth.getPrincipal()) : "<no-principal>";

        String tokenSubject = "<no-token>";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtService.validateToken(token);
                tokenSubject = claims.getSubject();
            } catch (Exception e) {
                tokenSubject = "<invalid-token: " + e.getClass().getSimpleName() + ": " + e.getMessage() + ">";
            }
        }

        StringBuilder headers = new StringBuilder();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String h = names.nextElement();
            if ("authorization".equalsIgnoreCase(h)) {
                headers.append(h).append(": <masked>\n");
            } else {
                headers.append(h).append(": ").append(request.getHeader(h)).append("\n");
            }
        }

        logger.warn("Authentication required (401) - method={} uri={}{} remote={} principal={} tokenSubject={} ; reason={}",
                method, uri, (query != null ? "?" + query : ""), remoteAddr, principalInfo, tokenSubject, authException.getMessage());

        logger.debug("Request headers:\n{}", headers.toString());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"status\":\"unauthorized\",\"message\":\"Authentication required\"}";
        response.getWriter().write(body);
    }
}
