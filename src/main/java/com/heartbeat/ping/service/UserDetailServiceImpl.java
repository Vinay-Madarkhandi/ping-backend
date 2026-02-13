package com.heartbeat.ping.service;

import com.heartbeat.ping.helpers.AuthUserDetails;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    public UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByEmail(username);
        if (user.isPresent()){
            return new AuthUserDetails(user.get());
        } else {
            throw new UsernameNotFoundException("Can not find user by email.");
        }
    }
}
