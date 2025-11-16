package com.vikrant.careSync.security;

import com.vikrant.careSync.security.filter.SecurityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final SecurityFilter securityFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.cors.allowed-origins:https://caresync-vikrant.vercel.app,http://localhost:4200}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/notifications/status").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/doctors/public/**").permitAll()
                        .requestMatchers("/api/patients/public/**").permitAll()
                        .requestMatchers("/api/doctors/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                        .requestMatchers("/api/patients/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/appointments/patient/**").hasRole("PATIENT")
                        .requestMatchers("/api/appointments/doctor/**").hasRole("DOCTOR")
                        .requestMatchers("/api/appointments/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/appointments/available-slots/**").authenticated()
                        .requestMatchers("/api/feedback/**").authenticated()
                        .requestMatchers("/api/medical-history/**").authenticated()
                        .requestMatchers("/api/analytics/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/reporting/**").hasAnyRole("DOCTOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ✅ CORS configuration bean
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Origin", "Accept", "Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}