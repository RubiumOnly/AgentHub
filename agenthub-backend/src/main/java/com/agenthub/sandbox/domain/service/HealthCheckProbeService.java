package com.agenthub.sandbox.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Probes HTTP endpoints with retries and timeout monitoring to determine deployment health.
 */
@Service
public class HealthCheckProbeService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckProbeService.class);

    private final HttpClient httpClient;

    public HealthCheckProbeService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public HealthCheckProbeService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Probes an HTTP endpoint until it returns a 2xx or 3xx status code, or retries are exhausted.
     *
     * @param url the target URL to probe
     * @param maxRetries maximum retry attempts
     * @param retryIntervalMs interval between attempts in milliseconds
     * @return true if endpoint responded with HTTP 2xx/3xx, false otherwise
     */
    public boolean probe(String url, int maxRetries, long retryIntervalMs) {
        if (url == null || url.isBlank()) {
            return false;
        }

        int attempts = Math.max(1, maxRetries);
        long interval = Math.max(100L, retryIntervalMs);

        for (int i = 1; i <= attempts; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 400) {
                    log.info("Health check passed for [{}] on attempt {} (status: {})", url, i, statusCode);
                    return true;
                } else {
                    log.debug("Health check probe [{}] returned non-success status {} on attempt {}", url, statusCode, i);
                }
            } catch (Exception e) {
                log.debug("Health check attempt {} for [{}] failed: {}", i, url, e.getMessage());
            }

            if (i < attempts) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        log.warn("Health check failed for [{}] after {} attempts", url, attempts);
        return false;
    }
}
