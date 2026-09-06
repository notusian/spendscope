package com.spendscope.recurring;

/**
 * How sure the detector is.
 *
 * <p>Surfaced to the user rather than hidden, because "we think this is a
 * monthly subscription" is a claim about their money and they deserve to know
 * how strong the evidence is.
 */
public enum RecurringConfidence {
    LOW,
    MEDIUM,
    HIGH
}
