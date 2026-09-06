package com.spendscope.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * The categories a user may reference: the shared built-ins plus their own.
     *
     * <p>This single predicate is the whole authorisation rule for categories,
     * which is why it lives in one named query rather than being re-derived at
     * each call site.
     */
    @Query("""
            SELECT c FROM Category c
            WHERE c.userId IS NULL OR c.userId = :userId
            ORDER BY c.name ASC
            """)
    List<Category> findAccessible(@Param("userId") Long userId);

    @Query("""
            SELECT c FROM Category c
            WHERE c.id = :categoryId AND (c.userId IS NULL OR c.userId = :userId)
            """)
    Optional<Category> findAccessibleById(@Param("categoryId") Long categoryId, @Param("userId") Long userId);

    /** Custom categories only - used when editing or deleting. */
    Optional<Category> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT COUNT(c) > 0 FROM Category c
            WHERE LOWER(c.name) = LOWER(:name) AND (c.userId IS NULL OR c.userId = :userId)
            """)
    boolean existsAccessibleByName(@Param("name") String name, @Param("userId") Long userId);

    Optional<Category> findByUserIdIsNullAndNameIgnoreCase(String name);
}
