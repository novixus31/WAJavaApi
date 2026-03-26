package com.chatin.security;

import com.chatin.model.User;
import com.chatin.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT Authentication Filter - equivalent to the authenticateToken middleware in
 * verify.ts
 * Extracts the JWT from the Authorization header and sets user info on the
 * request
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Allow CORS preflight requests bypass authentication
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip auth for login endpoint and health check
        if (path.equals("/api/auth/login") || path.equals("/api/auth/seed") || path.equals("/") || path.equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip auth for non-API routes
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Access denied. No token provided.\"}");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtTokenProvider.validateToken(token);
            String userId = claims.getSubject();

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"User not found.\"}");
                return;
            }

            User user = userOpt.get();

            // Set user info on request attributes (equivalent to req.user in Express)
            request.setAttribute("userId", user.getId());
            request.setAttribute("userEmail", user.getEmail());
            request.setAttribute("userRole", user.getRole());
            request.setAttribute("userCompanyId", user.getCompanyId());
            request.setAttribute("user", user);

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            logger.warn("JWT authentication failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid or expired token.\"}");
        }
    }
}
