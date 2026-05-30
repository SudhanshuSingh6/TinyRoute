package com.tinyroute.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final byte[] KEY_BYTES = new byte[64];

    static {
        Arrays.fill(KEY_BYTES, (byte) 7);
    }

    private final SecretKey signingKey = Keys.hmacShaKeyFor(KEY_BYTES);

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", Base64.getEncoder().encodeToString(KEY_BYTES));
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 900_000L);
        ReflectionTestUtils.invokeMethod(jwtService, "init");

        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String token(String type, long expiresInMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject("alice")
                .claim("roles", "ROLE_USER")
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMillis))
                .signWith(signingKey)
                .compact();
    }

    private MockFilterChain doFilterWith(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validAccessToken_setsAuthentication() throws Exception {
        MockFilterChain chain = doFilterWith("Bearer " + token("access", 900_000L));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull(); // chain continued
    }

    @Test
    void refreshTypeToken_doesNotAuthenticate() throws Exception {
        doFilterWith("Bearer " + token("refresh", 900_000L));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_doesNotAuthenticate() throws Exception {
        doFilterWith("Bearer " + token("access", -1_000L));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedToken_doesNotAuthenticate() throws Exception {
        doFilterWith("Bearer not-a-real-jwt");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noToken_passesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = doFilterWith(null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain continued
    }
}
