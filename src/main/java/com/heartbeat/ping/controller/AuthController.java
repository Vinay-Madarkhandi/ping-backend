package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.auth.AuthRequestDto;
import com.heartbeat.ping.dto.auth.AuthResponseDto;
import com.heartbeat.ping.dto.auth.UserSignUpRequestDto;
import com.heartbeat.ping.dto.auth.UserSignUpResponseDto;
import com.heartbeat.ping.service.AuthService;
import com.heartbeat.ping.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Value("${cookie.expiry}")
    private int cookieExpiry;

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
        Cookie[] str = request.getCookies();
        for (Cookie cookie : str) {
            System.out.println(cookie.getValue());
        }
        return ResponseEntity.ok(new AuthResponseDto(true));
    }


}
