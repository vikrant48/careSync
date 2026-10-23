package com.vikrant.careSync.security;

import com.vikrant.careSync.constants.AppConstants;
import com.vikrant.careSync.security.filter.JwtAuthenticationFilter;
import com.vikrant.careSync.security.filter.RateLimitingFilter;
import com.vikrant.careSync.security.filter.SecurityFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
        private final RateLimitingFilter rateLimitingFilter;
        private final AuthenticationProvider authenticationProvider;

        @Value("${app.cors.allowed-origins}")
        private String corsAllowedOrigins;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> {
                                })
                                .authorizeHttpRequests(auth -> auth
                                                // A streamed reply (AI chat SSE) ends with an async dispatch of the
                                                // same request, which carries no login of its own. The request was
                                                // already authorized on its first pass, so this only finishes it.
                                                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                                                .requestMatchers("/api/auth/login", "/api/auth/login/verify-code",
                                                                "/api/auth/register",
                                                                "/api/auth/google",
                                                                "/api/auth/refresh",
                                                                "/api/auth/logout", "/api/auth/forgot-password",
                                                                "/api/auth/forgot-password-otp",
                                                                "/api/auth/reset-password",
                                                                "/api/auth/reset-password-otp",
                                                                "/api/auth/verify-otp",
                                                                "/api/auth/check-availability",
                                                                "/api/auth/email-verification/**")
                                                .permitAll()
                                                .requestMatchers("/api/auth/change-password", "/api/auth/current-user")
                                                .authenticated()
                                                .requestMatchers("/ws/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/master/**").permitAll()
                                                .requestMatchers("/api/master/**").hasRole(AppConstants.Roles.ADMIN)

                                                .requestMatchers("/api/admin/**").hasRole(AppConstants.Roles.ADMIN)
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                .requestMatchers("/api/doctors/public/**").permitAll()
                                                .requestMatchers("/api/patients/public/**").permitAll()
                                                .requestMatchers("/api/seed/**").hasRole(AppConstants.Roles.ADMIN)
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
                                // No or expired login answers 401 (was 403), which the frontend turns into a
                                // token refresh or a new login; 403 stays for "logged in but not allowed"
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
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