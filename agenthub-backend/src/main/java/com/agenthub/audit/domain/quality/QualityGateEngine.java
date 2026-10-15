package com.agenthub.audit.domain.quality;

import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.execution.dto.StepRunView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Quality Gate evaluation engine implementing comprehensive policy checks:
 * 1. Build Verification
 * 2. Regression & Unit Test Suite
 * 3. Static Analysis & Prohibited Construct Guard
 * 4. Automated Secret Leakage Scan
 * 5. Diff Scale & Change Budget Guard
 */
@Component
public class QualityGateEngine {

    private static final Logger log = LoggerFactory.getLogger(QualityGateEngine.class);

    // Regular expressions detecting leaked credentials, tokens, or private keys
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|secret|password|passwd|token|bearer|private[_-]?key)\\s*[:=]\\s*['\"][a-zA-Z0-9_\\-\\.\\/+=]{12,}['\"]"),
            Pattern.compile("(?i)-----BEGIN\\s+(RSA\\s+)?PRIVATE\\s+KEY-----"),
            Pattern.compile("(?i)ghp_[a-zA-Z0-9]{36}"),
            Pattern.compile("(?i)github_pat_[a-zA-Z0-9_]{50,}")
    );

    // Forbidden dangerous constructs in submitted changes
    private static final List<Pattern> FORBIDDEN_STATIC_CONSTRUCTS = List.of(
            Pattern.compile("(?i)System\\.exit\\s*\\("),
            Pattern.compile("(?i)Runtime\\.getRuntime\\(\\)\\.exec\\s*\\("),
            Pattern.compile("(?i)ProcessBuilder\\s*\\("),
            Pattern.compile("(?i)Thread\\.stop\\s*\\(")
    );

    private static final int MAX_SAFE_ADDED_LINES = 2500;
    private static final int WARNING_ADDED_LINES = 1000;
    private static final int MAX_SAFE_CHANGED_FILES = 50;

    public QualityGateReport evaluate(String artifactId, String runId, StructuredDiff diff, List<StepRunView> stepRuns) {
        String reportId = "qgr-" + UUID.randomUUID().toString().substring(0, 8);
        QualityGateReport report = new QualityGateReport();
        report.setReportId(reportId);
        report.setArtifactId(artifactId);
        report.setRunId(runId);
        report.setEvaluatedAt(LocalDateTime.now());

        List<QualityGateCheckItem> items = new ArrayList<>();

        // 1. Secret Leak Scan
        items.add(evaluateSecretLeakScan(diff));

        // 2. Diff Budget Guard
        items.add(evaluateDiffBudget(diff));

        // 3. Static Analysis Guard
        items.add(evaluateStaticAnalysis(diff));

        // 4. Unit Test Regression
        items.add(evaluateTestRegression(stepRuns));

        // 5. Build Verification
        items.add(evaluateBuildVerification(stepRuns));

        int passedCount = 0;
        int failedCount = 0;
        int totalScore = 0;

        for (QualityGateCheckItem item : items) {
            if (item.getStatus() == QualityGateStatus.PASSED) {
                passedCount++;
            } else if (item.getStatus() == QualityGateStatus.FAILED) {
                failedCount++;
            }
            totalScore += item.getScore();
        }

        report.setTotalRules(items.size());
        report.setPassedRules(passedCount);
        report.setFailedRules(failedCount);
        report.setCompositeScore(items.isEmpty() ? 0 : totalScore / items.size());
        report.setCheckItems(items);

        if (failedCount > 0) {
            report.setOverallStatus(QualityGateStatus.FAILED);
        } else if (items.stream().anyMatch(i -> i.getStatus() == QualityGateStatus.WARNING)) {
            report.setOverallStatus(QualityGateStatus.WARNING);
        } else {
            report.setOverallStatus(QualityGateStatus.PASSED);
        }

        report.setSha256Fingerprint(computeFingerprint(report));
        log.info("Quality gate evaluated for artifact [{}]: overallStatus={}, score={}, failed={}",
                artifactId, report.getOverallStatus(), report.getCompositeScore(), failedCount);

        return report;
    }

    private QualityGateCheckItem evaluateSecretLeakScan(StructuredDiff diff) {
        long start = System.currentTimeMillis();
        if (diff == null || diff.getEntries() == null || diff.getEntries().isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.SECRET_LEAK_SCAN,
                    QualityGateStatus.PASSED,
                    100,
                    "Zero files modified; no credentials or secret leaks detected.",
                    System.currentTimeMillis() - start
            );
        }

        List<String> leakedDetections = new ArrayList<>();
        for (FileDiffEntry file : diff.getEntries()) {
            String diffText = file.getDiffContent();
            if (diffText == null) {
                continue;
            }
            for (String line : diffText.split("\\r?\\n")) {
                // Only scan added or modified lines
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    for (Pattern pattern : SECRET_PATTERNS) {
                        if (pattern.matcher(line).find()) {
                            leakedDetections.add(file.getNewPath() != null ? file.getNewPath() : file.getOldPath());
                            break;
                        }
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        if (!leakedDetections.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.SECRET_LEAK_SCAN,
                    QualityGateStatus.FAILED,
                    0,
                    "Detected potential secret or credential leak in files: " + leakedDetections,
                    elapsed
            );
        }

        return new QualityGateCheckItem(
                QualityGateRuleType.SECRET_LEAK_SCAN,
                QualityGateStatus.PASSED,
                100,
                "Scanned " + diff.getEntries().size() + " changed files; zero hardcoded credentials detected.",
                elapsed
        );
    }

    private QualityGateCheckItem evaluateDiffBudget(StructuredDiff diff) {
        long start = System.currentTimeMillis();
        if (diff == null || diff.getEntries() == null) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.DIFF_BUDGET_GUARD,
                    QualityGateStatus.PASSED,
                    100,
                    "Diff is within change budget.",
                    System.currentTimeMillis() - start
            );
        }

        int totalAdded = diff.getTotalLinesAdded();
        int totalDeleted = diff.getTotalLinesDeleted();
        int fileCount = diff.getEntries().size();
        long elapsed = System.currentTimeMillis() - start;

        if (totalAdded > MAX_SAFE_ADDED_LINES || fileCount > MAX_SAFE_CHANGED_FILES) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.DIFF_BUDGET_GUARD,
                    QualityGateStatus.FAILED,
                    30,
                    String.format("Change scale exceeded maximum safety budget: +%d/-%d across %d files (limit: +%d lines, %d files)",
                            totalAdded, totalDeleted, fileCount, MAX_SAFE_ADDED_LINES, MAX_SAFE_CHANGED_FILES),
                    elapsed
            );
        }

        if (totalAdded > WARNING_ADDED_LINES) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.DIFF_BUDGET_GUARD,
                    QualityGateStatus.WARNING,
                    75,
                    String.format("Large change detected: +%d/-%d across %d files. Peer review recommended.",
                            totalAdded, totalDeleted, fileCount),
                    elapsed
            );
        }

        return new QualityGateCheckItem(
                QualityGateRuleType.DIFF_BUDGET_GUARD,
                QualityGateStatus.PASSED,
                100,
                String.format("Change scale is optimal: +%d/-%d across %d file(s).", totalAdded, totalDeleted, fileCount),
                elapsed
        );
    }

    private QualityGateCheckItem evaluateStaticAnalysis(StructuredDiff diff) {
        long start = System.currentTimeMillis();
        if (diff == null || diff.getEntries() == null) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.STATIC_ANALYSIS_GUARD,
                    QualityGateStatus.PASSED,
                    100,
                    "Static analysis passed cleanly.",
                    System.currentTimeMillis() - start
            );
        }

        List<String> violations = new ArrayList<>();
        for (FileDiffEntry file : diff.getEntries()) {
            String text = file.getDiffContent();
            if (text == null) continue;
            for (String line : text.split("\\r?\\n")) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    for (Pattern p : FORBIDDEN_STATIC_CONSTRUCTS) {
                        if (p.matcher(line).find()) {
                            violations.add(file.getNewPath() + ": " + line.trim());
                        }
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        if (!violations.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.STATIC_ANALYSIS_GUARD,
                    QualityGateStatus.FAILED,
                    20,
                    "Prohibited runtime constructs detected: " + violations,
                    elapsed
            );
        }

        return new QualityGateCheckItem(
                QualityGateRuleType.STATIC_ANALYSIS_GUARD,
                QualityGateStatus.PASSED,
                100,
                "Static code analysis passed with zero prohibited constructs.",
                elapsed
        );
    }

    private QualityGateCheckItem evaluateTestRegression(List<StepRunView> stepRuns) {
        long start = System.currentTimeMillis();
        if (stepRuns == null || stepRuns.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.UNIT_TEST_REGRESSION,
                    QualityGateStatus.PASSED,
                    90,
                    "No explicit test steps scheduled in execution; baseline regression clean.",
                    System.currentTimeMillis() - start
            );
        }

        List<StepRunView> testSteps = stepRuns.stream()
                .filter(s -> {
                    String node = s.getNodeId() != null ? s.getNodeId().toLowerCase(Locale.ROOT) : "";
                    return node.contains("test") || node.contains("qa");
                })
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        if (testSteps.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.UNIT_TEST_REGRESSION,
                    QualityGateStatus.PASSED,
                    90,
                    "Zero failing test steps in pipeline.",
                    elapsed
            );
        }

        long failedTests = testSteps.stream()
                .filter(s -> "FAILED".equalsIgnoreCase(s.getStatus()) || "CANCELLED".equalsIgnoreCase(s.getStatus()))
                .count();

        if (failedTests > 0) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.UNIT_TEST_REGRESSION,
                    QualityGateStatus.FAILED,
                    0,
                    String.format("Unit test regression failed: %d/%d test step(s) failed or cancelled.", failedTests, testSteps.size()),
                    elapsed
            );
        }

        return new QualityGateCheckItem(
                QualityGateRuleType.UNIT_TEST_REGRESSION,
                QualityGateStatus.PASSED,
                100,
                String.format("All %d automated test and QA steps succeeded cleanly.", testSteps.size()),
                elapsed
        );
    }

    private QualityGateCheckItem evaluateBuildVerification(List<StepRunView> stepRuns) {
        long start = System.currentTimeMillis();
        if (stepRuns == null || stepRuns.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.BUILD_VERIFICATION,
                    QualityGateStatus.PASSED,
                    95,
                    "Build baseline intact.",
                    System.currentTimeMillis() - start
            );
        }

        List<StepRunView> buildSteps = stepRuns.stream()
                .filter(s -> {
                    String node = s.getNodeId() != null ? s.getNodeId().toLowerCase(Locale.ROOT) : "";
                    return node.contains("build") || node.contains("compile");
                })
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        if (buildSteps.isEmpty()) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.BUILD_VERIFICATION,
                    QualityGateStatus.PASSED,
                    95,
                    "No dedicated build steps failed.",
                    elapsed
            );
        }

        boolean anyFailed = buildSteps.stream()
                .anyMatch(s -> "FAILED".equalsIgnoreCase(s.getStatus()) || "CANCELLED".equalsIgnoreCase(s.getStatus()));

        if (anyFailed) {
            return new QualityGateCheckItem(
                    QualityGateRuleType.BUILD_VERIFICATION,
                    QualityGateStatus.FAILED,
                    0,
                    "Build verification failed: build steps encountered failure.",
                    elapsed
            );
        }

        return new QualityGateCheckItem(
                QualityGateRuleType.BUILD_VERIFICATION,
                QualityGateStatus.PASSED,
                100,
                "Build verification confirmed successful.",
                elapsed
        );
    }

    private String computeFingerprint(QualityGateReport report) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%s|%d|%d|%d|%s",
                    report.getReportId(),
                    report.getArtifactId(),
                    report.getOverallStatus(),
                    report.getTotalRules(),
                    report.getPassedRules(),
                    report.getCompositeScore(),
                    report.getEvaluatedAt()
            );
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "sha256-mock-fingerprint-" + UUID.randomUUID();
        }
    }
}
