package com.spendscope.security;

import com.spendscope.user.UserRepository;
import com.spendscope.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads a user by email for Spring Security's DaoAuthenticationProvider. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(UserService.normaliseEmail(email))
                .map(AppUserDetails::from)
                // DaoAuthenticationProvider converts this into BadCredentials so
                // the response cannot distinguish "unknown email" from
                // "wrong password".
                .orElseThrow(() -> new UsernameNotFoundException("No user for the given email"));
    }
}
