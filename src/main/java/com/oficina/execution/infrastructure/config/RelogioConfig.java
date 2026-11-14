package com.oficina.execution.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
