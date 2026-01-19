package com.vikrant.careSync.security;

import com.vikrant.careSync.constants.AppConstants;
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
                                .cors(cors -> {
                                })
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/auth/check-availability").permitAll()
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/api/master/**").permitAll()
                                                .requestMatchers("/api/notifications/status").permitAll()
                                                .requestMatchers("/api/admin/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.ADMIN)
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                .requestMatchers("/api/doctors/public/**").permitAll()
                                                .requestMatchers("/api/patients/public/**").permitAll()
                                                .requestMatchers("/api/doctors/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.PATIENT,
                                                                AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/patients/**")
                                                .hasAnyRole(AppConstants.Roles.PATIENT, AppConstants.Roles.DOCTOR,
                                                                AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/appointments/patient/**")
                                                .hasRole(AppConstants.Roles.PATIENT)
                                                .requestMatchers("/api/ai/chat")
                                                .hasAnyRole(AppConstants.Roles.PATIENT, AppConstants.Roles.DOCTOR)
                                                .requestMatchers("/api/ai/summarize/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/appointments/doctor/**")
                                                .hasRole(AppConstants.Roles.DOCTOR)
                                                .requestMatchers("/api/appointments/admin/**")
                                                .hasRole(AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/appointments/available-slots/**").authenticated()
                                                .requestMatchers("/api/feedback/**").hasRole(AppConstants.Roles.PATIENT)
                                                .requestMatchers("/api/medical-history/**").authenticated()
                                                .requestMatchers("/api/analytics/doctor/**")
                                                .hasRole(AppConstants.Roles.DOCTOR)
                                                .requestMatchers("/api/analytics/patient/**")
                                                .hasRole(AppConstants.Roles.PATIENT)
                                                .requestMatchers("/api/analytics/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/reporting/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.ADMIN)
                                                .requestMatchers("/api/files/**")
                                                .hasAnyRole(AppConstants.Roles.DOCTOR, AppConstants.Roles.PATIENT,
                                                                AppConstants.Roles.ADMIN)
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                config.setAllowedHeaders(Arrays.asList("Origin", "Accept", "Authorization", "Cache-Control",
                                "Content-Type", "X-Requested-With"));
                config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return new CorsFilter(source);
        }
}