package com.family.chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Spring MVC Configuration to serve React Single Page Application (SPA):
 * - Forwards root '/' to '/index.html'
 * - Enables deep linking for client-side React routes while leaving '/api' and '/webhook' endpoints intact.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // Non-API and non-webhook paths fall back to index.html for React SPA
                        if (!resourcePath.startsWith("api") && !resourcePath.startsWith("webhook") && !resourcePath.startsWith("swagger") && !resourcePath.startsWith("v3/api-docs")) {
                            return location.createRelative("index.html");
                        }
                        return null;
                    }
                });
    }
}
