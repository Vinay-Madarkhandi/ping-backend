package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.auth.AuthRequestDto;
import com.heartbeat.ping.dto.auth.AuthResponseDto;
import com.heartbeat.ping.dto.auth.ChangePasswordRequest;
import com.heartbeat.ping.dto.auth.ForgotPasswordRequest;
import com.heartbeat.ping.dto.auth.MeResponse;
import com.heartbeat.ping.dto.auth.ResetPasswordRequest;
import com.heartbeat.ping.dto.auth.UserSignUpRequestDto;
import com.heartbeat.ping.dto.auth.UserSignUpResponseDto;
import com.heartbeat.ping.dto.auth.VerifyEmailRequest;
import com.heartbeat.ping.service.AuthService;
import com.heartbeat.ping.service.EmailVerificationService;
import com.heartbeat.ping.service.JwtService;
import com.heartbeat.ping.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Value("${cookie.expiry}")
    private int cookieExpiry;

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager, 
                         JwtService jwtService, PasswordResetService passwordResetService,
                         EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/signup/user")
    public ResponseEntity<UserSignUpResponseDto> signUp(@RequestBody UserSignUpRequestDto userSignUpRequest){

        UserSignUpResponseDto responseDto = authService.userSignUp(userSignUpRequest);

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @PostMapping("/signin/user")
    public ResponseEntity<AuthResponseDto> signIn(@RequestBody AuthRequestDto authRequestDto, HttpServletResponse response){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequestDto.getEmail(),
                        authRequestDto.getPassword()
                )
        );

        String token = jwtService.createToken(authRequestDto.getEmail());

        ResponseCookie responseCookie = ResponseCookie.from("JwtToken",token)
                    .httpOnly(true)
                    .secure(false)
                    .maxAge(cookieExpiry)
                    .path("/")
                    .build();

        response.setHeader(HttpHeaders.SET_COOKIE ,responseCookie.toString());
        return new ResponseEntity<>( new AuthResponseDto(true), HttpStatus.OK);


    }

    @GetMapping("/validate")
    public ResponseEntity<AuthResponseDto> validate(HttpServletRequest request){
        return ResponseEntity.ok(new AuthResponseDto(true));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication){
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }

    /**
     * Change the authenticated user's password. Requires the current password for verification.
     * Does NOT invalidate existing sessions — logout handles that separately.
     */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        authService.changePassword(
                authentication.getName(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * Initiate password reset by sending a reset email. Always returns success to prevent email
     * enumeration — even if the email doesn't exist, we return 200 (but don't send anything).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * Complete password reset using the token from the email. Validates token, updates password,
     * and invalidates all reset tokens for the user.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * Verify email using token from verification email. Marks user as verified.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.getToken());
        return ResponseEntity.ok().build();
    }

    /**
     * Resend verification email to the authenticated user. Idempotent - does nothing if already verified.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        emailVerificationService.sendVerificationEmail(authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Real logout: revokes the current JWT by adding its jti to the revoked tokens table.
     * The token remains cryptographically valid but will be rejected by the auth filter.
     * Client should also delete the cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Extract token from cookie
        String token = extractTokenFromCookie(request);
        if (token != null) {
            jwtService.revokeToken(token);
        }

        // Clear the cookie
        ResponseCookie cookie = ResponseCookie.from("JwtToken", "")
                .httpOnly(true)
                .secure(false)
                .maxAge(0)
                .path("/")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }

    /**
     * Delete the authenticated user's account. This is irreversible and triggers:
     * - Soft delete with anonymization (email becomes "deleted-{id}@deleted.local")
     * - Cascade deletion of all monitors, incidents, tokens via database FK constraints
     * - Payment records are retained (anonymized) for tax/legal compliance
     * - User is immediately logged out
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        authService.deleteAccount(authentication.getName());
        
        // Revoke current token and clear cookie
        String token = extractTokenFromCookie(request);
        if (token != null) {
            jwtService.revokeToken(token);
        }

        ResponseCookie cookie = ResponseCookie.from("JwtToken", "")
                .httpOnly(true)
                .secure(false)
                .maxAge(0)
                .path("/")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.noContent().build();
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if ("JwtToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

}
