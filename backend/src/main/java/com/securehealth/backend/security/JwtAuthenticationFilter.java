package com.securehealth.backend.security;

import com.securehealth.backend.util.JwtUtil;
import com.securehealth.backend.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that intercepts HTTP requests to validate JWT authentication.
 * <p>
 * Extracts the "Authorization" header, verifies the token's validity and revocation status, 
 * and populates the {@link SecurityContextHolder} if the token is valid.
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * Performs the actual filter logic to extract and validate the JWT.
     *
     * @param request the {@link HttpServletRequest}
     * @param response the {@link HttpServletResponse}
     * @param chain the {@link FilterChain}
     * @throws ServletException in case of servlet errors
     * @throws IOException in case of I/O errors
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // ✅ 1. SKIP JWT for Prometheus / Actuator
        String path = request.getServletPath();
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        String email = null;
        String jwt = null;


        // 0. Bypass filter for CORS preflight (OPTIONS) requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Check for Bearer token
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);

            if (tokenBlacklistService.isBlacklisted(jwt)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
                return;
            }

            try {
                email = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                logger.debug("Rejected unreadable JWT: {}", e.getClass().getSimpleName());
            }
        }


        // 2. Validate Token & Set Security Context
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            String idleKey = idleKeyFor(jwt, email);
            if (tokenBlacklistService.isSessionIdle(idleKey)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired due to inactivity");
                return;
            }

            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(email);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account no longer exists");
                return;
            }
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is disabled");
                return;
            }


            if (jwtUtil.validateToken(jwt, userDetails)) {

                tokenBlacklistService.updateLastActive(idleKey);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        chain.doFilter(request, response);
    }

    /** Idle tracking is per login session ({@code s:<id>}); tokens without a session id fall back to the e-mail. */
    private String idleKeyFor(String jwt, String email) {
        try {
            String sid = jwtUtil.extractSessionId(jwt);
            return sid != null ? "s:" + sid : email;
        } catch (RuntimeException e) {
            return email;
        }
    }
}
