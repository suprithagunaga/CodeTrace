package com.codetrace.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the local Vite development server (configurable via CORS_ALLOWED_ORIGINS). */
@Configuration
@EnableConfigurationProperties(CodeTraceProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CodeTraceProperties properties;

    public WebConfig(CodeTraceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Location")
                .maxAge(3600);
    }
}
