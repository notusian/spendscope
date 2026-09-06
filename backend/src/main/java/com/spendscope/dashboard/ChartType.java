package com.spendscope.dashboard;

/** How a widget renders its data. */
public enum ChartType {
    /** A single formatted number. */
    NUMBER,
    /** A single number plus its percentage change against the comparison period. */
    CHANGE,
    LINE,
    BAR,
    PIE,
    DONUT,
    /** A ranked list of labels and values. */
    LIST,
    /** Progress bars, used by the budget widget. */
    PROGRESS
}
