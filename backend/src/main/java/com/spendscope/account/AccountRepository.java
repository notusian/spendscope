package com.spendscope.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code userId}.
 *
 * <p>There is deliberately no {@code findById(id)} in use anywhere in the
 * service layer: an id arriving from a client is a request, not a proof of
 * ownership. Making the scoped variant the only convenient option is how the
 * codebase stays free of IDOR bugs.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    List<Account> findByUserIdOrderByArchivedAscNameAsc(Long userId);

    List<Account> findByUserIdAndArchivedFalseOrderByNameAsc(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByIdAndUserId(Long id, Long userId);
}
