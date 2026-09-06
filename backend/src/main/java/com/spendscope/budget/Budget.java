package com.spendscope.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A monthly spending limit for one category.
 *
 * <p>Note what is absent: any "spent" or "remaining" column. Those are derived
 * from transactions on every read. Storing them would mean every transaction
 * insert, edit, delete, import and re-categorisation has to update the budget
 * too - and the first path that forgets leaves the user staring at a number
 * that is quietly wrong.
 *
 * <p>{@link #month} is the first day of the budgeted month, enforced by a check
 * constraint, so months sort and compare as ordinary dates.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private LocalDate month;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
        // Required by JPA.
    }

    public Budget(Long userId, Long categoryId, YearMonth month, BigDecimal amount) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.month = month.atDay(1);
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public LocalDate getMonth() {
        return month;
    }

    public YearMonth getYearMonth() {
        return YearMonth.from(month);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Budget budget) || id == null) {
            return false;
        }
        return id.equals(budget.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
