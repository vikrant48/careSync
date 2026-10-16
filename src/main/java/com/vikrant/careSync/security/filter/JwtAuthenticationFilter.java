package com.vikrant.careSync.security.filter;

import com.vikrant.careSync.security.CustomUserDetailsService;
import com.vikrant.careSync.security.JwtService;
import com.vikrant.careSync.security.service.SecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityService securityService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No valid auth header, skipping JWT processing");
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            username = jwtService.extractUsername(jwt);
            log.debug("Extracted username: {}", username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                log.debug("Loaded user details for: {}", userDetails.getUsername());
                log.debug("User authorities: {}", userDetails.getAuthorities());

                // A valid signature is not enough: the login session must still be live (not logged out,
                // revoked, idle too long or too old). The check also records session activity.
                boolean sessionUsable = userDetails.isEnabled() && jwtService.isTokenValid(jwt, userDetails)
                        && securityService.isSessionUsable(jwtService.extractSessionId(jwt));
                if (sessionUsable) {
                    log.debug("JWT token is valid");
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authentication set in SecurityContext");
                } else if (!userDetails.isEnabled()) {
                    log.warn("Inactive user attempted to use an existing token: {}", username);
                } else {
                    log.debug("JWT token rejected: invalid, expired or its session has ended");
                }
            } else {
                log.debug("Username is null or authentication already exists");
            }
        } catch (Exception e) {
            log.error("Error processing JWT: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}
