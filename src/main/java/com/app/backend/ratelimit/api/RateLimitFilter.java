package com.app.backend.ratelimit.api;

import com.app.backend.ratelimit.config.RateLimitProperties;
import com.app.backend.ratelimit.domain.RateLimitDecision;
import com.app.backend.ratelimit.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class RateLimitFilter extends OncePerRequestFilter {

    public static final String LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    static final String RETRY_AFTER_PROPERTY = "retryAfterSeconds";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final String headerName;
    private final List<PathPattern> protectedPaths;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.headerName = properties.headerName();
        this.protectedPaths = properties.pathPatterns().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(PATH_HELPER.getPathWithinApplication(request));
        return protectedPaths.stream().noneMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(headerName);
        if (apiKey == null || apiKey.isBlank()) {
            writeProblem(request, response, missingApiKey());
            return;
        }

        RateLimitDecision decision = rateLimiter.tryConsume(apiKey.strip());
        response.setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
        response.setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            writeProblem(request, response, tooManyRequests(decision.retryAfterSeconds()));
            return;
        }
        chain.doFilter(request, response);
    }

    private ProblemDetail missingApiKey() {
        return problem(HttpStatus.UNAUTHORIZED, "Missing API key: send it in the " + headerName + " header");
    }

    private static ProblemDetail tooManyRequests(long retryAfterSeconds) {
        ProblemDetail body = problem(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded: retry after " + retryAfterSeconds + " seconds");
        body.setProperty(RETRY_AFTER_PROPERTY, retryAfterSeconds);
        return body;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        return body;
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, ProblemDetail body)
            throws IOException {
        body.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(body.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
