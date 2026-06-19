package com.sarada.trading.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React app from the configured static locations and falls
 * back to {@code index.html} for unknown paths so client-side routes
 * (e.g. /history, /analytics) survive a full page reload.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/dist/", "classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = super.getResource(resourcePath, location);
                        if (requested != null && requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
