package com.ainexus.hospital.patient.config;

import com.ainexus.hospital.patient.security.BlacklistCheckFilter;
import com.ainexus.hospital.patient.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final BlacklistCheckFilter blacklistCheckFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          BlacklistCheckFilter blacklistCheckFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.blacklistCheckFilter = blacklistCheckFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless JWT — no CSRF needed
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**",
                                 "/actuator/info", "/actuator/prometheus",
                                 "/api/v1/auth/login",       // login is public
                                 "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )

            // Return 401 for unauthenticated requests (not a redirect)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // Both filters run before UsernamePasswordAuthenticationFilter.
            // Insertion order determines execution: blacklistCheckFilter first, then jwtAuthFilter (AD-002).
            .addFilterBefore(blacklistCheckFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Prevent JwtAuthFilter from being auto-registered as a standalone servlet filter
     * by Spring Boot (since it is @Component). It should only run inside the Security
     * filter chain (registered via addFilterBefore above).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevent BlacklistCheckFilter from being auto-registered as a standalone servlet filter.
     * It runs only inside the Security filter chain (before JwtAuthFilter).
     */
    @Bean
    public FilterRegistrationBean<BlacklistCheckFilter> blacklistFilterRegistration(
            BlacklistCheckFilter filter) {
        FilterRegistrationBean<BlacklistCheckFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
