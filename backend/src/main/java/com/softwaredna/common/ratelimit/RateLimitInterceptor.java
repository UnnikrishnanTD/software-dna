package com.softwaredna.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Enforces a {@link RateLimiter} for one endpoint, keyed by client IP.
 *
 * <p>The IP is read from {@code X-Forwarded-For} first. Railway, like every
 * platform that fronts an app with a load balancer, terminates TLS and
 * proxies the request, so {@link HttpServletRequest#getRemoteAddr()} would
 * otherwise return the proxy's address for every caller and the limiter
 * would treat all clients as one.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter limiter;
    private final String endpointName;
    private final ObjectMapper json;

    public RateLimitInterceptor(RateLimiter limiter, String endpointName, ObjectMapper json) {
        this.limiter = limiter;
        this.endpointName = endpointName;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        // The path pattern this is registered against also matches GET (e.g.
        // listing analyses on the same /api/analyses path); only the request
        // that actually does the expensive work should be limited.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = clientIpOf(request);

        if (limiter.tryAcquire(clientIp)) {
            return true;
        }

        long retryAfter = limiter.secondsUntilReset(clientIp);
        log.warn("Rate limit exceeded for {} from {}", endpointName, clientIp);

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write(json.writeValueAsString(ApiError.of(
                429,
                ErrorCode.CLIENT_RATE_LIMITED.name(),
                "Too many requests. Try again in " + retryAfter + " seconds.",
                request.getRequestURI())));
        return false;
    }

    /**
     * The first address in {@code X-Forwarded-For}, which is the original
     * client on every proxy this application expects to sit behind. Falls
     * back to the direct connection when the header is absent, which is the
     * case in local development.
     */
    private String clientIpOf(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
