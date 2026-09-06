package com.spendscope.anomaly;

/**
 * How far outside the usual range a category's spending is.
 *
 * <p>Named for magnitude, not for blame. SpendScope has no idea whether an
 * unusual month is a fraud, a holiday or a new sofa, so the vocabulary stays
 * descriptive.
 */
public enum AnomalySeverity {
    NOTABLE,
    HIGH
}
