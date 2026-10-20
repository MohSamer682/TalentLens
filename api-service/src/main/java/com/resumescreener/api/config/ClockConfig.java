package com.resumescreener.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Injected wherever expiry or "now" matters, so tests can fix the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
