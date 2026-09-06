package com.spendscope.transaction;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic WHERE clause for the transaction list endpoint.
 *
 * <p>The Criteria API is used rather than string concatenation: every value
 * becomes a bound parameter, so a search term containing a quote or a
 * semicolon is data, never SQL.
 */
public final class TransactionSpecifications {

    /** Escape character declared in the LIKE clause below. */
    private static final char LIKE_ESCAPE = '\\';

    private TransactionSpecifications() {
    }

    /**
     * @param userId the authenticated user - always applied, never optional.
     *               It is the first argument for the same reason it is the
     *               first column of every index: nothing is ever queried
     *               across users.
     */
    public static Specification<Transaction> forUser(Long userId, TransactionQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));

            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("date"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("date"), query.to()));
            }
            if (query.categoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), query.categoryId()));
            }
            if (query.accountId() != null) {
                predicates.add(builder.equal(root.get("accountId"), query.accountId()));
            }

            String search = query.normalisedSearch();
            if (search != null) {
                String pattern = "%" + escapeLikeWildcards(search) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("normalizedDescription")), pattern, LIKE_ESCAPE),
                        builder.like(builder.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        builder.like(builder.lower(root.get("merchant")), pattern, LIKE_ESCAPE)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Neutralises LIKE metacharacters in user input.
     *
     * <p>Not an injection defence - the value is already a bound parameter -
     * but without it a search for "50%" would match everything containing "50".
     */
    private static String escapeLikeWildcards(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
