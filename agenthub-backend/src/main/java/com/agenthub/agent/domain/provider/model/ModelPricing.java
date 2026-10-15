package com.agenthub.agent.domain.provider.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Model Pricing and Token Cost Calculation Engine.
 * Supports leading LLM pricing models (OpenAI, DeepSeek, Anthropic, Gemini, Ollama)
 * as well as custom provider-level pricing overrides and cached token discounts.
 */
public final class ModelPricing {

    public record PriceRate(double inputPerMillion, double outputPerMillion, double cachedInputPerMillion) {
        public PriceRate(double inputPerMillion, double outputPerMillion) {
            this(inputPerMillion, outputPerMillion, inputPerMillion * 0.5);
        }
    }

    private static final Map<String, PriceRate> DEFAULT_RATES = Map.ofEntries(
            // OpenAI
            Map.entry("gpt-4o", new PriceRate(5.00, 15.00, 2.50)),
            Map.entry("gpt-4o-mini", new PriceRate(0.15, 0.60, 0.075)),
            Map.entry("gpt-4-turbo", new PriceRate(10.00, 30.00, 5.00)),
            Map.entry("gpt-3.5-turbo", new PriceRate(0.50, 1.50, 0.25)),
            Map.entry("o1", new PriceRate(15.00, 60.00, 7.50)),
            Map.entry("o3-mini", new PriceRate(1.10, 4.40, 0.55)),
            // DeepSeek
            Map.entry("deepseek-chat", new PriceRate(0.14, 0.28, 0.014)),
            Map.entry("deepseek-reasoner", new PriceRate(0.55, 2.19, 0.14)),
            // Anthropic
            Map.entry("claude-3-5-sonnet", new PriceRate(3.00, 15.00, 0.30)),
            Map.entry("claude-3-5-haiku", new PriceRate(0.80, 4.00, 0.08)),
            Map.entry("claude-3-haiku", new PriceRate(0.25, 1.25, 0.025)),
            Map.entry("claude-3-opus", new PriceRate(15.00, 75.00, 1.50)),
            // Gemini
            Map.entry("gemini-1.5-pro", new PriceRate(1.25, 5.00, 0.3125)),
            Map.entry("gemini-1.5-flash", new PriceRate(0.075, 0.30, 0.01875)),
            Map.entry("gemini-2.0-flash", new PriceRate(0.10, 0.40, 0.025)),
            // Local & Mock
            Map.entry("ollama", new PriceRate(0.00, 0.00, 0.00)),
            Map.entry("llama", new PriceRate(0.00, 0.00, 0.00)),
            Map.entry("vllm", new PriceRate(0.00, 0.00, 0.00)),
            Map.entry("mock", new PriceRate(0.00, 0.00, 0.00))
    );

    private static final PriceRate FALLBACK_RATE = new PriceRate(1.00, 2.00, 0.50);

    private ModelPricing() {}

    /**
     * Resolves the pricing rate for a given model.
     */
    public static PriceRate getRateForModel(String model) {
        if (model == null || model.isBlank()) {
            return FALLBACK_RATE;
        }
        String normalized = model.toLowerCase(Locale.ROOT).trim();

        // Exact match check
        if (DEFAULT_RATES.containsKey(normalized)) {
            return DEFAULT_RATES.get(normalized);
        }

        // Substring / prefix check
        for (Map.Entry<String, PriceRate> entry : DEFAULT_RATES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return FALLBACK_RATE;
    }

    /**
     * Expose immutable map of canonical model pricing rates.
     */
    public static Map<String, PriceRate> getAllRates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULT_RATES));
    }

    /**
     * Calculates estimated cost based on token counts and optional provider custom pricing.
     */
    public static double calculateCost(String model,
                                       int promptTokens,
                                       int completionTokens) {
        return calculateCost(model, promptTokens, completionTokens, null, null);
    }

    public static double calculateCost(String model,
                                       int promptTokens,
                                       int completionTokens,
                                       Double customInputPerMillion,
                                       Double customOutputPerMillion) {
        return calculateCostWithCache(model, promptTokens, completionTokens, 0, customInputPerMillion, customOutputPerMillion);
    }

    public static double calculateCostWithCache(String model,
                                                int promptTokens,
                                                int cachedTokens,
                                                int completionTokens) {
        return calculateCostWithCache(model, promptTokens, completionTokens, cachedTokens, null, null);
    }

    /**
     * Calculates estimated cost taking cached input tokens into account.
     */
    public static double calculateCostWithCache(String model,
                                                int promptTokens,
                                                int completionTokens,
                                                int cachedTokens,
                                                Double customInputPerMillion,
                                                Double customOutputPerMillion) {
        PriceRate rate = getRateForModel(model);
        double inputRate = (customInputPerMillion != null && customInputPerMillion > 0)
                ? customInputPerMillion : rate.inputPerMillion();
        double outputRate = (customOutputPerMillion != null && customOutputPerMillion > 0)
                ? customOutputPerMillion : rate.outputPerMillion();
        double cacheRate = rate.cachedInputPerMillion();

        int regularPrompt = Math.max(0, promptTokens - Math.max(0, cachedTokens));
        int effectiveCached = Math.min(Math.max(0, promptTokens), Math.max(0, cachedTokens));

        double rawCost = (regularPrompt * inputRate
                + effectiveCached * cacheRate
                + Math.max(0, completionTokens) * outputRate) / 1_000_000.0;

        return BigDecimal.valueOf(rawCost)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
