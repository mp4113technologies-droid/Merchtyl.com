package com.merchtyl.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.common.ApiError;
import com.merchtyl.security.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class MerchantContextFilter extends OncePerRequestFilter {
    private final MerchantPortalService portals;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public MerchantContextFilter(MerchantPortalService portals, UserRepository users, ObjectMapper objectMapper) {
        this.portals = portals;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String slug = request.getHeader(MerchantPortalService.HEADER_NAME);
        if (slug == null || slug.isBlank() || request.getRequestURI().startsWith("/api/v1/public/") || request.getRequestURI().startsWith("/api/v1/platform/")) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            var user = users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
            try {
                if (user == null || user.getTenantId() == null || !user.getTenantId().equals(portals.tenantId(slug))) {
                    deny(request, response);
                    return;
                }
            } catch (RuntimeException exception) {
                deny(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError("MERCHANT_CONTEXT_MISMATCH", "Access is denied for this merchant portal", 403,
                request.getRequestURI(), request.getMethod(), MDC.get("correlationId"), List.of(), Instant.now()));
    }
}
