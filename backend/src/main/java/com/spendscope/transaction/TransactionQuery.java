package com.spendscope.transaction;

import com.spendscope.common.error.BadRequestException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Query parameters for {@code GET /api/transactions}.
 *
 * <p>Bound from the query string. Every value is validated or clamped here so
 * the service never sees an unbounded page size or an arbitrary sort column.
 *
 * @param sort      one of {@link #SORTABLE_FIELDS}; an allowlist rather than a
 *                  passthrough, because Spring Data will happily sort by any
 *                  property name it is given and error messages from an invalid
 *                  one leak the entity's field names
 */
public record TransactionQuery(
        LocalDate from,
        LocalDate to,
        Long categoryId,
        Long accountId,
        @Size(max = 100, message = "Search must be at most 100 characters") String search,
        @Min(value = 0, message = "Page must be zero or greater") Integer page,
        @Min(value = 1, message = "Size must be at least 1")
        @Max(value = MAX_PAGE_SIZE, message = "Size must be at most " + MAX_PAGE_SIZE) Integer size,
        String sort,
        String direction) {

    /**
     * A hard ceiling. Without one, {@code ?size=1000000} is a cheap way to make
     * the server materialise every row a user owns.
     */
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 25;

    public static final Set<String> SORTABLE_FIELDS = Set.of("date", "amount", "description", "createdAt");

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * @throws BadRequestException when the range is inverted, which is almost
     *         always a client bug and would otherwise silently return nothing
     */
    public void validateDateRange() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
    }

    public PageRequest toPageRequest() {
        String sortField = sort == null || sort.isBlank() ? "date" : sort;
        if (!SORTABLE_FIELDS.contains(sortField)) {
            throw new BadRequestException(
                    "Sort must be one of " + SORTABLE_FIELDS.stream().sorted().toList());
        }
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction == null ? "" : direction.trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // "id" is appended as a tiebreaker so paging is stable when many rows
        // share a date - without it, the same row can appear on two pages.
        return PageRequest.of(pageOrDefault(), sizeOrDefault(),
                Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    public String normalisedSearch() {
        return search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
    }
}
