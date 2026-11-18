package io.flagship.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**", "/login")
            )
            .headers(headers -> headers
                .addHeaderWriter(new XFrameOptionsHeaderWriter(
                    XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN))
            )
            .authorizeHttpRequests(auth -> auth
                // Static resources & H2 console
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/h2-console/**").permitAll()
                // SDK endpoints are open to client SDKs with optional API key header
                .requestMatchers("/api/v1/sdk/**").permitAll()
                // Actuator endpoints
                .requestMatchers("/actuator/**").permitAll()
                // Login page
                .requestMatchers("/login").permitAll()
                // Approvals require APPROVER or ADMIN
                .requestMatchers("/approvals/action/**", "/api/v1/admin/approvals/*/decision").hasAnyRole("APPROVER", "ADMIN")
                // Modification actions require EDITOR or ADMIN
                .requestMatchers(HttpMethod.POST, "/flags/**", "/api/v1/admin/**").hasAnyRole("EDITOR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/flags/**", "/api/v1/admin/**").hasAnyRole("EDITOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/flags/**", "/api/v1/admin/**").hasAnyRole("ADMIN")
                .requestMatchers("/users/**").hasRole("ADMIN")
                // All other UI pages require authentication (VIEWER, EDITOR, APPROVER, ADMIN)
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
}
