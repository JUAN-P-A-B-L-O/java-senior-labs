package com.jpcore.labs.paymentauthorization.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class RequestLogContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get("requestedBy");
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
                MDC.put("requestedBy", jwt.getName().replaceAll("[^a-zA-Z0-9._@-]", "_"));
            } else {
                MDC.remove("requestedBy");
            }
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove("requestedBy");
            else MDC.put("requestedBy", previous);
        }
    }
}
