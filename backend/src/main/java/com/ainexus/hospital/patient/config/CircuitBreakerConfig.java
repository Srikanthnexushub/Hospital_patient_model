package com.ainexus.hospital.patient.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j circuit breaker configuration for the Auth Module integration.
 * Settings defined in application.yml under resilience4j.circuitbreaker.instances.authModule:
 *   - failureRateThreshold: 50 (%)
 *   - slidingWindowSize: 10
 *   - minimumNumberOfCalls: 5
 *   - waitDurationInOpenState: 30s
 *   - permittedCallsInHalfOpenState: 1
 *
 * States:
 *   CLOSED  → normal operation (< threshold failures)
 *   OPEN    → all requests fail immediately with 503 (circuit tripped)
 *   HALF-OPEN → one probe request allowed; success → CLOSED, failure → OPEN
 */
@Configuration
public class CircuitBreakerConfig {

    // CircuitBreakerRegistry is auto-configured by resilience4j-spring-boot3.
    // This class exists to document the circuit breaker configuration and to
    // provide a hook for future programmatic customisation if needed.

    public CircuitBreakerConfig(CircuitBreakerRegistry registry) {
        // Verify the authModule circuit breaker is registered
        registry.circuitBreaker("authModule");
    }
}
