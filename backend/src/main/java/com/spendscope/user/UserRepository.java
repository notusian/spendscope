package com.spendscope.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Used by the scheduled cleanup of expired demo accounts. */
    List<User> findByDemoTrueAndCreatedAtBefore(Instant cutoff);
}
