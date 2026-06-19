package com.janne6565.stratabackend.configuration.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Adds the {@code /api} prefix to every controller centrally, so the {@code *Api} interfaces only
 * declare their versioned path (e.g. {@code /v1/auth}) and the full route becomes {@code
 * /api/v1/auth}. Keeps the API prefix in one place (cosy convention).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api",
                HandlerTypePredicate.forBasePackage("com.janne6565.stratabackend.controller"));
    }
}
