package com.agenthub.audit.domain.quality;

/**
 * Supported quality gate rule dimensions for code delivery defense.
 */
public enum QualityGateRuleType {

    /**
     * Build and compilation verification (e.g. Maven compile, Next.js build).
     */
    BUILD_VERIFICATION,

    /**
     * Regression and unit test suite verification (zero failures/errors).
     */
    UNIT_TEST_REGRESSION,

    /**
     * Static code style, layering architecture, and complexity guard.
     */
    STATIC_ANALYSIS_GUARD,

    /**
     * Automated credential and sensitive secret leakage scan.
     */
    SECRET_LEAK_SCAN,

    /**
     * Change scale and diff budget quota protection (lines/files limit).
     */
    DIFF_BUDGET_GUARD
}
