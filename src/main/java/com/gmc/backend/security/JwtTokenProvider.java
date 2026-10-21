package com.gmc.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    // Access token validity: 15 minutes
    private final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000;

    // Secret key used for JWT signing and verification (Base64). Comes from jwt.secret so tokens
    // survive restarts and work across instances; a random key is used only when none is set.
    private final String secretKey;

    public JwtTokenProvider() {
        this(null);
    }

    @Autowired
    public JwtTokenProvider(@Value("${jwt.secret:}") String configuredSecret) {
        byte[] decoded = decodeOrEmpty(configuredSecret);
        if (decoded.length >= 32) {
            this.secretKey = configuredSecret.trim();
        } else {
            if (configuredSecret != null && !configuredSecret.isBlank()) {
                LoggerFactory.getLogger(JwtTokenProvider.class)
                        .warn("jwt.secret is shorter than 256 bits; using a random key (tokens reset on restart)");
            }
            this.secretKey = Base64.getEncoder()
                    .encodeToString(Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded());
        }
    }

    private static byte[] decodeOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private SecretKey getSigningKey() {
        try {
            return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey));
        } catch (IllegalArgumentException ex) {
            return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    //  Generate Access Token with extra claims
    public String generateToken(
            org.springframework.security.core.userdetails.UserDetails userDetails, Map<String, Object> extraClaims) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //  Extract username (email)
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    //  Extract expiration
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //  Extract any claim
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    //  Validate token
    public boolean validateToken(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    //  Check expiration
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    //  Parse claims
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
