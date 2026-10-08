package com.agenthub.agent.domain.provider.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Model Pricing and Token Cost Calculation Engine.
 * Supports leading LLM pricing models (OpenAI, DeepSeek, Anthropic, Gemini, Ollama)
 * as well as custom provider-level pricing overrides.
 */
public final class ModelPricing {

    public record PriceRate(double inputPerMillion, double outputPerMillion) {}

    private static final Map<String, PriceRate> DEFAULT_RATES = Map.ofEntries(
            // OpenAI
            Map.entry("gpt-4o", new PriceRate(5.00, 15.00)),
            Map.entry("gpt-4o-mini", new PriceRate(0.15, 0.60)),
            Map.entry("gpt-4-turbo", new PriceRate(10.00, 30.00)),
            Map.entry("gpt-3.5-turbo", new PriceRate(0.50, 1.50)),
            // DeepSeek
            Map.entry("deepseek-chat", new PriceRate(0.14, 0.28)),
            Map.entry("deepseek-reasoner", new PriceRate(0.55, 2.19)),
            // Anthropic
            Map.entry("claude-3-5-sonnet", new PriceRate(3.00, 15.00)),
            Map.entry("claude-3-haiku", new PriceRate(0.25, 1.25)),
            Map.entry("claude-3-opus", new PriceRate(15.00, 75.00)),
            // Gemini
            Map.entry("gemini-1.5-pro", new PriceRate(1.25, 5.00)),
            Map.entry("gemini-1.5-flash", new PriceRate(0.075, 0.30)),
            Map.entry("gemini-2.0-flash", new PriceRate(0.10, 0.40)),
            // Local & Mock
            Map.entry("ollama", new PriceRate(0.00, 0.00)),
            Map.entry("llama", new PriceRate(0.00, 0.00)),
            Map.entry("vllm", new PriceRate(0.00, 0.00)),
            Map.entry("mock", new PriceRate(0.00, 0.00))
    );

    private static final PriceRate FALLBACK_RATE = new PriceRate(1.00, 2.00);

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
     * Calculates estimated cost based on token counts and optional provider custom pricing.
     */
    public static double calculateCost(String model,
                                        int promptTokens,
                                        int completionTokens,
                                        Double customInputPerMillion,
                                        Double customOutputPerMillion) {
        double inputRate;
        double outputRate;

        if (customInputPerMillion != null && customInputPerMillion > 0) {
            inputRate = customInputPerMillion;
        } else {
            inputRate = getRateForModel(model).inputPerMillion();
        }

        if (customOutputPerMillion != null && customOutputPerMillion > 0) {
            outputRate = customOutputPerMillion;
        } else {
            outputRate = getRateForModel(model).outputPerMillion();
        }

        double rawCost = (promptTokens * inputRate + completionTokens * outputRate) / 1_000_000.0;
        return BigDecimal.valueOf(rawCost)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
