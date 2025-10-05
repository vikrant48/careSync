package com.vikrant.careSync.security;

import com.vikrant.careSync.security.service.SecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityService securityService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        
        System.out.println("=== JWT FILTER DEBUG ===");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Auth Header: " + authHeader);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("No valid auth header, skipping JWT processing");
            filterChain.doFilter(request, response);
            return;
        }
        
        jwt = authHeader.substring(7);
        System.out.println("JWT Token: " + jwt.substring(0, Math.min(jwt.length(), 50)) + "...");
        
        try {
            username = jwtService.extractUsername(jwt);
            System.out.println("Extracted username: " + username);
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                System.out.println("Loaded user details for: " + userDetails.getUsername());
                System.out.println("User authorities: " + userDetails.getAuthorities());
                
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    System.out.println("JWT token is valid");
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println("Authentication set in SecurityContext");
                    
                    // Update session activity automatically
                    try {
                        String sessionId = jwtService.extractSessionId(jwt);
                        if (sessionId != null) {
                            securityService.updateSessionActivity(sessionId);
                            System.out.println("Session activity updated for sessionId: " + sessionId);
                        }
                    } catch (Exception e) {
                        System.out.println("Error updating session activity: " + e.getMessage());
                    }
                } else {
                    System.out.println("JWT token is invalid");
                }
            } else {
                System.out.println("Username is null or authentication already exists");
            }
        } catch (Exception e) {
            System.out.println("Error processing JWT: " + e.getMessage());
            e.printStackTrace();
        }
        
        filterChain.doFilter(request, response);
    }
}