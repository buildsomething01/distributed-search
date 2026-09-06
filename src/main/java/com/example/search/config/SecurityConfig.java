package com.example.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("api")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.security.enabled:true}") boolean securityEnabled) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if (securityEnabled) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health/**").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
