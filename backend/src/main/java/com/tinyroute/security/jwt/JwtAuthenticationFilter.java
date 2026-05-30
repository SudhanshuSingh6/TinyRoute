package com.tinyroute.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = jwtService.resolveAccessToken(request);

        if (SecurityContextHolder.getContext().getAuthentication() != null || jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Parse + verify signature and expiry once, then read every claim
            // from this single Claims instance.
            Claims claims = jwtService.parseClaims(jwt);

            if (jwtService.isAccessToken(claims)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                claims.getSubject(),
                                null,
                                jwtService.getAuthorities(claims)
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );
                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
            }

        } catch (JwtException | IllegalArgumentException e) {

            log.warn(
                    "Could not set user authentication from token: {}",
                    e.getMessage()
            );
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}