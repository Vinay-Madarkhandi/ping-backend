package com.heartbeat.ping.helpers;

import com.heartbeat.ping.modles.User;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthUserDetails extends User implements UserDetails {
    private String username;
    private String password;

    public AuthUserDetails(User user){
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        // Carried over from the wrapped user so isEnabled()/getCreatedAt() below reflect the real
        // account state rather than this object's own (uninitialized) BaseModel fields.
        this.setId(user.getId());
        this.setCreatedAt(user.getCreatedAt());
        this.setDeletedAt(user.getDeletedAt());
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

    /**
     * A soft-deleted account (see {@link User#getDeletedAt()}) must not authenticate — not just on
     * signin (checked here via Spring Security's account-status pre-check) but also for any JWT
     * issued before the deletion; see {@code JwtAuthenticationFilters}, which checks this directly
     * since it does not go through {@code DaoAuthenticationProvider}.
     */
    @Override
    public boolean isEnabled() {
        return getDeletedAt() == null;
    }
}
