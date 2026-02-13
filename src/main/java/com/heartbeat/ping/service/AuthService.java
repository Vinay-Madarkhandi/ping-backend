package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.auth.UserSignUpRequestDto;
import com.heartbeat.ping.dto.auth.UserSignUpResponseDto;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    public UserSignUpResponseDto userSignUp(UserSignUpRequestDto userSignUpRequest) {
        User user = User.builder()
                .userName(userSignUpRequest.getUsername())
                .email(userSignUpRequest.getEmail())
                .passwordHash(bCryptPasswordEncoder.encode(userSignUpRequest.getPassword()))
                .build();
        User response = userRepository.save(user);

        return UserSignUpResponseDto.from(response);
    }
}
