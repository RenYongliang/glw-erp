package com.glw.backend.auth;

import com.glw.backend.config.SecurityProperties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

/** Filter and authorization rules use the same method/path matchers. */
@Component
public class PublicEndpoints implements RequestMatcher {
    private final List<RequestMatcher> matchers;

    public PublicEndpoints(SecurityProperties properties) {
        matchers =
                properties.whitelist().stream()
                        .map(
                                e ->
                                        (RequestMatcher)
                                                PathPatternRequestMatcher.withDefaults()
                                                        .matcher(e.method(), e.path()))
                        .toList();
    }

    public boolean matches(HttpServletRequest request) {
        return matchers.stream().anyMatch(m -> m.matches(request));
    }
}
