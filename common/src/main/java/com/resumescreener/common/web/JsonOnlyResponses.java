package com.resumescreener.common.web;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Both services speak JSON only, whatever the client asks for and wherever the error is raised. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({StandardHost.class, WebMvcConfigurer.class})
public class JsonOnlyResponses implements WebMvcConfigurer {

    /** A browser sends Accept: text/html; without this every endpoint answers it with an empty 406. */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonTomcatErrors() {
        return factory -> factory.addContextCustomizers(context -> {
            // Sits inside any stock valve Boot adds, so it reports first and the stock one stands down
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
            }
        });
    }
}
