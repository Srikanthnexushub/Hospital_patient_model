package com.ainexus.hospital.patient.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the BCryptPasswordEncoder bean for the Auth Module.
 * Strength 12 balances security (OWASP HIPAA recommendation) and performance
 * (~400ms per hash — within the 2-second login SLA).
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
