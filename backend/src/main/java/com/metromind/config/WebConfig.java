package com.metromind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-origin configuration for the API.
 *
 * <p>The future React frontend will call {@code /api/**} from the Vercel origin,
 * so cross-origin access is enabled per-origin (never permissive {@code *}).
 * The allowed origin(s) come from the {@code app.cors.allowed-origins} property
 * (comma-separated) and default to the Vite development origin, so local
 * frontend development works out of the box. In production, set the property to
 * the deployed frontend's origin. Other endpoints such as
 * {@code /api/health} remain unaffected except for the same origin policy.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}