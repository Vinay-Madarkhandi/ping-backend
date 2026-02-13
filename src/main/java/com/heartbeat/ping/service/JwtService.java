package com.heartbeat.ping.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    @Value("${jwt.expiry}")
    private long expiry;

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    public String generateToken(Map<String, Object> payload, String email) {
            return Jwts.builder()
                    .addClaims(payload)
                    .setExpiration(new Date(System.currentTimeMillis() + expiry*100))
                    .setIssuedAt(new Date())
                    .setSubject(email)
                    .signWith(getSignKey(), SignatureAlgorithm.HS256)
                    .compact();
    }

    public String createToken(String email){
        return Jwts.builder()
                .setExpiration(new Date(System.currentTimeMillis() + expiry*100L))
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

    public Date extractExpiration(String token){
        return extractClaim(token, Claims::getExpiration);
    }

    public Key getSignKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    public Boolean isValid(String token){
        final String subjectFetched = extractEmail(token);
        return !isTokenExpired(token) && subjectFetched.equals(token);
    }

    public Boolean validateToken(String token, String email) {
        return email.equals(extractEmail(token)) && !isTokenExpired(token);
    }
}