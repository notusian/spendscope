package com.spendscope.transaction;

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
import java.util.Objects;

/**
 * A single money movement.
 *
 * <p><b>Sign convention:</b> {@link #amount} is signed. Negative means money
 * left the account (a purchase), positive means money arrived (salary, refund).
 * Storing the sign rather than a separate "direction" column means a period
 * total is a plain SUM, and a refund correctly cancels out the purchase it
 * reverses.
 *
 * <p><b>date vs createdAt:</b> {@link #date} is the calendar day the money
 * moved, as the user thinks of it - a {@code LocalDate} with no timezone,
 * because "I bought coffee on the 3rd" does not change meaning when the user
 * flies to another country. {@link #createdAt} is a real instant in UTC
 * describing when the row was written.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Null means "uncategorised"; the user can assign one later. */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "normalized_description", nullable = false, length = 500)
    private String normalizedDescription;

    @Column(length = 120)
    private String merchant;

    /** NUMERIC(19,4) in PostgreSQL. Never a float. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** SHA-256 hex digest used for duplicate detection. See TransactionFingerprint. */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // Required by JPA.
    }

    public Transaction(Long userId,
                       Long accountId,
                       Long categoryId,
                       LocalDate date,
                       String description,
                       String normalizedDescription,
                       String merchant,
                       BigDecimal amount,
                       String currency,
                       String fingerprint) {
        this.userId = userId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.date = date;
        this.description = description;
        this.normalizedDescription = normalizedDescription;
        this.merchant = merchant;
        this.amount = amount;
        this.currency = currency;
        this.fingerprint = fingerprint;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNormalizedDescription() {
        return normalizedDescription;
    }

    public void setNormalizedDescription(String normalizedDescription) {
        this.normalizedDescription = normalizedDescription;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction transaction) || id == null) {
            return false;
        }
        return id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
