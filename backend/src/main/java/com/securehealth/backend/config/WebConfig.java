package com.securehealth.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * General web configuration for the application.
 * <p>
 * This class implements {@link WebMvcConfigurer} to customize Spring MVC settings,
 * specifically for CORS mappings to allow frontend access.
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Configures CORS mappings for the application.
     * <p>
     * It enables global CORS configuration, allowing specified origins, methods, 
     * and headers, and supports credentials with a defined max age for pre-flight requests.
     * </p>
     *
     * @param registry the {@link CorsRegistry} to add mappings to
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOrigins(java.util.Arrays.stream(origins).map(String::trim).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
