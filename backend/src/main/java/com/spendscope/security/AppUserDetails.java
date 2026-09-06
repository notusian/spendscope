package com.spendscope.security;

import com.spendscope.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Carrying the database {@code userId} here is what makes every ownership
 * check cheap: controllers read the id straight off the principal, never from
 * a request parameter, so a client cannot claim to be someone else.
 */
public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final boolean demo;

    public AppUserDetails(Long userId, String email, String passwordHash, boolean demo) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.demo = demo;
    }

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.isDemo());
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public boolean isDemo() {
        return demo;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // SpendScope has a single kind of user; authorisation is by data
        // ownership, not by role. See docs/security.md.
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
