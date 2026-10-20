package com.resumescreener.api.portal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PortalProperties.class)
class PortalConfig {

    @Bean
    FilterRegistrationBean<InviteThrottle> inviteThrottle(PortalProperties properties, Clock clock) {
        FilterRegistrationBean<InviteThrottle> registration =
                new FilterRegistrationBean<>(new InviteThrottle(properties.inviteRequestsPerMinute(), clock));
        registration.addUrlPatterns("/api/portal/invites/*");
        return registration;
    }
}
