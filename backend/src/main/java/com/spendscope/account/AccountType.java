package com.spendscope.account;

/** The kind of financial account, mirroring the ck_accounts_type check constraint. */
public enum AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    INVESTMENT,
    OTHER
}
