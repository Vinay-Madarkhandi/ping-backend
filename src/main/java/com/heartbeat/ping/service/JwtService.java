package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.RevokedToken;
import com.heartbeat.ping.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {
    @Value("${jwt.expiry}")
    private long expiry;

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    private final RevokedTokenRepository revokedTokenRepository;

    public String generateToken(Map<String, Object> payload, String email) {
            return Jwts.builder()
                    .addClaims(payload)
                    .setExpiration(new Date(System.currentTimeMillis() + expiry * 1000L))
                    .setIssuedAt(new Date())
                    .setSubject(email)
                    .signWith(getSignKey(), SignatureAlgorithm.HS256)
                    .compact();
    }

    public String createToken(String email){
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setId(jti)  // Add JWT ID for revocation tracking
                .setExpiration(new Date(System.currentTimeMillis() + expiry * 1000L))
                .setIssuedAt(new Date())
                .setSubject(email)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllPayloads(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllPayloads(String token) {
        return (Claims) Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Object getPayloadByKey(String token, String payLoadKey){
        Claims claims = extractAllPayloads(token);
        return (Object) claims.get(payLoadKey);
    }

    public String extractEmail(String token){
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token){
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token){
        return extractClaim(token, Claims::getExpiration);
    }

    public Date extractIssuedAt(String token){
        return extractClaim(token, Claims::getIssuedAt);
    }

    public Key getSignKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    public Boolean isTokenRevoked(String token){
        String jti = extractJti(token);
        return jti != null && revokedTokenRepository.existsByJti(jti);
    }

    public Boolean isValid(String token){
        final String subjectFetched = extractEmail(token);
        return subjectFetched != null && !isTokenExpired(token) && !isTokenRevoked(token);
    }

    public Boolean validateToken(String token, String email) {
        return email.equals(extractEmail(token)) && !isTokenExpired(token) && !isTokenRevoked(token);
    }

    /**
     * Revoke a token by adding its jti to the revoked tokens table. The token remains
     * cryptographically valid but will fail authentication checks.
     */
    public void revokeToken(String token) {
        String jti = extractJti(token);
        if (jti == null) {
            // Old tokens created before jti was added can't be revoked individually
            return;
        }

        String email = extractEmail(token);
        Date expiration = extractExpiration(token);

        RevokedToken revokedToken = RevokedToken.builder()
                .jti(jti)
                .userEmail(email)
                .expiresAt(expiration.toInstant())
                .build();

        revokedTokenRepository.save(revokedToken);
    }

    /**
     * Cleanup expired revoked tokens. Should be called periodically by a scheduled job.
     */
    public int cleanupExpiredRevokedTokens() {
        return revokedTokenRepository.deleteExpiredTokens(Instant.now());
    }
}
