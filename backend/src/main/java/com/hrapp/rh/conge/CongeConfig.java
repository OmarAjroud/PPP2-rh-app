package com.hrapp.rh.conge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CongeProperties.class)
public class CongeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}