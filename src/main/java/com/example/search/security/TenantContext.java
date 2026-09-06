package com.example.search.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TenantContext {
    private final boolean securityEnabled;
    private final String tenantClaim;

    public TenantContext(
            @Value("${app.security.enabled:true}") boolean securityEnabled,
            @Value("${app.security.tenant-claim:tenant_id}") String tenantClaim) {
        this.securityEnabled = securityEnabled;
        this.tenantClaim = tenantClaim;
    }

    public String tenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String tenantId = jwt.getToken().getClaimAsString(tenantClaim);
            if (StringUtils.hasText(tenantId)) {
                return tenantId;
            }
        }

        if (!securityEnabled) {
            String tenantId = request.getHeader("X-Tenant-Id");
            if (StringUtils.hasText(tenantId)) {
                return tenantId;
            }
        }

        throw new IllegalArgumentException("Tenant context is missing");
    }
}
