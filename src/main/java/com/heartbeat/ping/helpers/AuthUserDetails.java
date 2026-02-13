package com.heartbeat.ping.helpers;

import com.heartbeat.ping.modles.User;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AuthUserDetails extends User implements UserDetails {
    private String username;
    private String password;

    public AuthUserDetails(User user){
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public @Nullable String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }
}
