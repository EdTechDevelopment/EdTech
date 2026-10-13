package io.github.edtechdevelopment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
