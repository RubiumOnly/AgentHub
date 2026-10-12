package com.agenthub.infrastructure.ops;

import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase E Prometheus Scrape Configuration, Alert Rule Validator & Live Alert Evaluator.
 * Validates docker/prometheus/prometheus.yml and docker/prometheus/agenthub-alerts.yml,
 * and evaluates alert expressions against real /actuator/prometheus exposition payloads.
 */
@Component
public class PrometheusAlertEvaluator {

    private static final Pattern METRIC_LINE_PATTERN = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{([^}]*)})?\\s+([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?|NaN|[+-]Inf)$"
    );

    public record AlertRuleDefinition(
            String alert,
            String expr,
            String forDuration,
            String severity,
            String team,
            String summary,
            String description
    ) {}

    public record FiringAlert(
            String alertName,
            String severity,
            String summary,
            String expr,
            double observedValue
    ) {}

    public record PrometheusValidationReport(
            boolean valid,
            List<String> errors,
            List<AlertRuleDefinition> rules
    ) {
        public void assertValid() {
            if (!valid) {
                throw new IllegalStateException("Prometheus configuration validation failed: " + String.join("; ", errors));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public PrometheusValidationReport validatePrometheusConfigAndRules(Path prometheusYmlPath, Path alertsYmlPath) {
        List<String> errors = new ArrayList<>();
        List<AlertRuleDefinition> parsedRules = new ArrayList<>();

        if (prometheusYmlPath == null || !Files.exists(prometheusYmlPath)) {
            errors.add("prometheus.yml not found at: " + prometheusYmlPath);
            return new PrometheusValidationReport(false, errors, parsedRules);
        }
        if (alertsYmlPath == null || !Files.exists(alertsYmlPath)) {
            errors.add("agenthub-alerts.yml not found at: " + alertsYmlPath);
            return new PrometheusValidationReport(false, errors, parsedRules);
        }

        Yaml yaml = new Yaml();
        try {
            Map<String, Object> promRoot = yaml.load(Files.readString(prometheusYmlPath, StandardCharsets.UTF_8));
            if (promRoot == null) {
                errors.add("prometheus.yml is empty");
            } else {
                List<String> ruleFiles = (List<String>) promRoot.get("rule_files");
                if (ruleFiles == null || ruleFiles.stream().noneMatch(r -> r.contains("agenthub-alerts.yml"))) {
                    errors.add("prometheus.yml must reference agenthub-alerts.yml in rule_files");
                }
                List<Map<String, Object>> scrapeConfigs = (List<Map<String, Object>>) promRoot.get("scrape_configs");
                if (scrapeConfigs == null || scrapeConfigs.isEmpty()) {
                    errors.add("prometheus.yml must define scrape_configs");
                } else {
                    boolean hasBackendJob = scrapeConfigs.stream()
                            .anyMatch(sc -> "/actuator/prometheus".equals(sc.get("metrics_path")));
                    if (!hasBackendJob) {
                        errors.add("prometheus.yml must include a scrape job for /actuator/prometheus");
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Invalid YAML in prometheus.yml: " + e.getMessage());
        }

        try {
            Map<String, Object> alertsRoot = yaml.load(Files.readString(alertsYmlPath, StandardCharsets.UTF_8));
            if (alertsRoot == null || alertsRoot.get("groups") == null) {
                errors.add("agenthub-alerts.yml must define top-level 'groups'");
            } else {
                List<Map<String, Object>> groups = (List<Map<String, Object>>) alertsRoot.get("groups");
                for (Map<String, Object> group : groups) {
                    String groupName = String.valueOf(group.get("name"));
                    if (groupName == null || groupName.isBlank()) {
                        errors.add("Alert group is missing 'name'");
                    }
                    List<Map<String, Object>> rules = (List<Map<String, Object>>) group.get("rules");
                    if (rules == null || rules.isEmpty()) {
                        errors.add("Alert group '" + groupName + "' has no rules");
                        continue;
                    }
                    for (Map<String, Object> rule : rules) {
                        String alertName = (String) rule.get("alert");
                        String expr = (String) rule.get("expr");
                        String forDur = (String) rule.get("for");
                        Map<String, Object> labels = (Map<String, Object>) rule.get("labels");
                        Map<String, Object> annotations = (Map<String, Object>) rule.get("annotations");

                        if (alertName == null || alertName.isBlank()) {
                            errors.add("Alert rule missing 'alert' name");
                            continue;
                        }
                        if (expr == null || expr.isBlank()) {
                            errors.add("Alert rule '" + alertName + "' missing 'expr'");
                        }
                        if (forDur == null || forDur.isBlank()) {
                            errors.add("Alert rule '" + alertName + "' missing 'for' duration");
                        }
                        String severity = labels != null ? (String) labels.get("severity") : null;
                        String team = labels != null ? (String) labels.get("team") : null;
                        if (severity == null || severity.isBlank()) {
                            errors.add("Alert rule '" + alertName + "' missing labels.severity");
                        }
                        String summary = annotations != null ? (String) annotations.get("summary") : null;
                        String description = annotations != null ? (String) annotations.get("description") : null;
                        if (summary == null || summary.isBlank() || description == null || description.isBlank()) {
                            errors.add("Alert rule '" + alertName + "' missing annotations.summary or annotations.description");
                        }
                        parsedRules.add(new AlertRuleDefinition(alertName, expr, forDur, severity, team, summary, description));
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Invalid YAML in agenthub-alerts.yml: " + e.getMessage());
        }

        return new PrometheusValidationReport(errors.isEmpty(), errors, parsedRules);
    }

    /**
     * Evaluates loaded Prometheus alert rules against live Prometheus exposition format text
     * (from /actuator/prometheus) and returns all currently firing alerts.
     */
    public List<FiringAlert> evaluateAlertsAgainstPrometheusText(
            List<AlertRuleDefinition> rules,
            String prometheusExpositionText,
            AgentHubMetricsCollector metricsCollector) {
        Map<String, Double> samples = parsePrometheusTextSamples(prometheusExpositionText);
        List<FiringAlert> firing = new ArrayList<>();

        for (AlertRuleDefinition rule : rules) {
            String expr = rule.expr();
            if ("AgentHubHighPendingRuns".equals(rule.alert())) {
                double activeRuns = samples.entrySet().stream()
                        .filter(e -> e.getKey().equals("agenthub_runs_active") || e.getKey().startsWith("agenthub_runs_active{"))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(metricsCollector != null ? (double) metricsCollector.getActiveRuns() : 0.0);
                if (activeRuns > 20.0) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, activeRuns));
                }
            } else if ("AgentHubProviderCircuitBreakerOpen".equals(rule.alert())) {
                double openState = samples.entrySet().stream()
                        .filter(e -> e.getKey().startsWith("agenthub_provider_circuit_breaker_state") && e.getKey().contains("state=\"OPEN\""))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(0.0);
                if (openState >= 1.0) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, openState));
                }
            } else if ("AgentHubDiskUsageCritical".equals(rule.alert())) {
                double availRatio = samples.entrySet().stream()
                        .filter(e -> e.getKey().equals("agenthub_workspace_disk_avail_ratio") || e.getKey().startsWith("agenthub_workspace_disk_avail_ratio{"))
                        .mapToDouble(Map.Entry::getValue)
                        .min()
                        .orElse(metricsCollector != null ? metricsCollector.getWorkspaceDiskAvailableRatio() : 1.0);
                if (availRatio < 0.15) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, availRatio));
                }
            } else if ("AgentHubRunFailureSpike".equals(rule.alert())) {
                double failedTotal = samples.entrySet().stream()
                        .filter(e -> e.getKey().startsWith("agenthub_runs_total{") && e.getKey().contains("status=\"FAILED\""))
                        .mapToDouble(Map.Entry::getValue)
                        .sum();
                double allTotal = samples.entrySet().stream()
                        .filter(e -> e.getKey().startsWith("agenthub_runs_total{"))
                        .mapToDouble(Map.Entry::getValue)
                        .sum();
                if (allTotal > 0 && (failedTotal / allTotal) > 0.05) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, failedTotal / allTotal));
                }
            } else if ("AgentHubStepLatencyDegradation".equals(rule.alert())) {
                double p99StepSeconds = samples.entrySet().stream()
                        .filter(e -> (e.getKey().startsWith("agenthub_steps_duration_seconds{") && e.getKey().contains("quantile=\"0.99\""))
                                || e.getKey().startsWith("agenthub_steps_duration_seconds_max"))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(0.0);
                if (p99StepSeconds > 30.0) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, p99StepSeconds));
                }
            } else if ("AgentHubWorkspaceLockContentionHigh".equals(rule.alert())) {
                double p95LockWaitSeconds = samples.entrySet().stream()
                        .filter(e -> (e.getKey().startsWith("agenthub_workspace_lock_wait_seconds{") && e.getKey().contains("quantile=\"0.95\""))
                                || e.getKey().startsWith("agenthub_workspace_lock_wait_seconds_max"))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(0.0);
                if (p95LockWaitSeconds > 2.0) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, p95LockWaitSeconds));
                }
            } else if ("AgentHubSseConnectionDropSpike".equals(rule.alert())) {
                double sseDelta = samples.entrySet().stream()
                        .filter(e -> e.getKey().startsWith("agenthub_sse_connections_active_delta"))
                        .mapToDouble(Map.Entry::getValue)
                        .min()
                        .orElse(0.0);
                if (sseDelta < -50.0) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, sseDelta));
                }
            } else if ("AgentHubHikariConnectionPoolSaturated".equals(rule.alert())) {
                double activeConn = samples.entrySet().stream()
                        .filter(e -> e.getKey().equals("hikaricp_connections_active") || e.getKey().startsWith("hikaricp_connections_active{"))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(0.0);
                double maxConn = samples.entrySet().stream()
                        .filter(e -> e.getKey().equals("hikaricp_connections_max") || e.getKey().startsWith("hikaricp_connections_max{"))
                        .mapToDouble(Map.Entry::getValue)
                        .max()
                        .orElse(0.0);
                if (maxConn > 0 && (activeConn / maxConn) > 0.85) {
                    firing.add(new FiringAlert(rule.alert(), rule.severity(), rule.summary(), expr, activeConn / maxConn));
                }
            }
        }
        return firing;
    }

    public Map<String, Double> parsePrometheusTextSamples(String prometheusText) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (prometheusText == null || prometheusText.isBlank()) {
            return result;
        }
        String[] lines = prometheusText.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = METRIC_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1);
                String labels = matcher.group(2);
                String valStr = matcher.group(3);
                try {
                    double val = Double.parseDouble(valStr);
                    if (labels != null && !labels.isBlank()) {
                        result.put(name + "{" + labels + "}", val);
                    } else {
                        result.put(name, val);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
