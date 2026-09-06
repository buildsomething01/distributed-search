package com.example.search.ratelimit;

import com.example.search.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final TenantContext tenantContext;
    private final RedisRateLimiter rateLimiter;

    public RateLimitInterceptor(TenantContext tenantContext, RedisRateLimiter rateLimiter) {
        this.tenantContext = tenantContext;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/v1/")) {
            return true;
        }

        String tenantId = tenantContext.tenantId(request);
        try {
            if (!rateLimiter.allow(tenantId)) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Tenant rate limit exceeded");
                return false;
            }
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable; allowing request");
        }
        return true;
    }
}
