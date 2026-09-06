package com.spendscope.user;

import com.spendscope.common.error.ConflictException;
import com.spendscope.common.error.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creation and lookup of users. Password hashing lives here so no other class
 * ever touches a plain text password.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @throws ConflictException when the email is already registered.
     */
    @Transactional
    public User create(String email, String rawPassword, boolean demo) {
        String normalisedEmail = normaliseEmail(email);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new ConflictException("An account with that email already exists");
        }
        User user = new User(normalisedEmail, passwordEncoder.encode(rawPassword), demo);
        return userRepository.save(user);
    }

    /**
     * Loads a user by id.
     *
     * <p>This is the one place in the codebase that calls {@code findById}
     * without an ownership predicate, and it is safe because the only thing it
     * ever loads is the caller themselves: both call sites pass
     * {@code principal.getUserId()}, which came from the session. It is "look
     * up my own account", not "look up any account by id".
     *
     * <p>Every other entity is fetched through a {@code findByIdAndUserId}
     * style query. See docs/security.md.
     */
    @Transactional(readOnly = true)
    public User require(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    @Transactional
    public void changePassword(Long userId, String newRawPassword) {
        User user = require(userId);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
    }

    /**
     * Emails are compared case-insensitively and without surrounding
     * whitespace, so normalising on the way in lets the database's plain
     * UNIQUE constraint do the de-duplication.
     */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
