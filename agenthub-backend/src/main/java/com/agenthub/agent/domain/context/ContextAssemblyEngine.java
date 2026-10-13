package com.agenthub.agent.domain.context;

import com.agenthub.agent.domain.definition.ToolRegistry;
import com.agenthub.project.domain.context.SecurityLevel;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ContextAssemblyEngine {

    private final ToolRegistry toolRegistry;

    public ContextAssemblyEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public static class LayerInput {
        public ContextLayerType type;
        public String content;
        public int budget;
        public ContextTruncationStrategy strategy;
        public SecurityLevel securityLevel;
        public String sourceRef;

        public LayerInput(ContextLayerType type,
                          String content,
                          int budget,
                          ContextTruncationStrategy strategy,
                          SecurityLevel securityLevel,
                          String sourceRef) {
            this.type = type;
            this.content = content;
            this.budget = budget;
            this.strategy = strategy;
            this.securityLevel = securityLevel;
            this.sourceRef = sourceRef;
        }
    }

    public AssembledContext assemble(List<LayerInput> layerInputs) {
        if (layerInputs == null || layerInputs.isEmpty()) {
            return new AssembledContext(0, 0, true, Collections.emptyList(), "");
        }

        List<ContextLayer> processedLayers = new ArrayList<>();
        StringBuilder promptBuilder = new StringBuilder();
        int totalTokens = 0;
        int totalBudget = 0;

        for (LayerInput input : layerInputs) {
            if (input == null || input.content == null) continue;

            String raw = input.content;
            if (input.type == ContextLayerType.TOOL_RESULTS && input.sourceRef != null) {
                raw = toolRegistry.guardOutputSize(input.sourceRef, raw);
            }

            int estimatedTokens = estimateTokens(raw);
            totalBudget += input.budget;

            String finalContent = raw;
            boolean truncated = false;

            if (estimatedTokens > input.budget && input.budget > 0) {
                truncated = true;
                finalContent = applyTruncation(raw, input.budget, input.strategy);
                estimatedTokens = estimateTokens(finalContent);
            }

            totalTokens += estimatedTokens;

            ContextLayer layer = new ContextLayer(
                    input.type,
                    finalContent,
                    input.budget,
                    estimatedTokens,
                    truncated,
                    input.strategy,
                    input.securityLevel,
                    input.sourceRef
            );
            processedLayers.add(layer);

            promptBuilder.append("=== [CONTEXT: ").append(input.type.name())
                    .append(" (Security: ").append(input.securityLevel.name())
                    .append(truncated ? ", TRUNCATED" : "")
                    .append(")] ===\n")
                    .append(finalContent.trim())
                    .append("\n\n");
        }

        boolean withinBudget = totalTokens <= totalBudget;
        return new AssembledContext(totalTokens, totalBudget, withinBudget, processedLayers, promptBuilder.toString().trim());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Approximation: ~4 characters per token
        return (int) Math.ceil((double) text.length() / 4.0);
    }

    private String applyTruncation(String text, int tokenBudget, ContextTruncationStrategy strategy) {
        int maxChars = tokenBudget * 4;
        if (text.length() <= maxChars) return text;

        return switch (strategy) {
            case TRUNCATE_TAIL -> text.substring(0, maxChars) + "\n... [TRUNCATED_TAIL: Exceeded token budget " + tokenBudget + "]";
            case TRUNCATE_HEAD -> "[TRUNCATED_HEAD: Prior content omitted] ...\n" + text.substring(text.length() - maxChars);
            case SUMMARIZE -> {
                int headChars = (int) (maxChars * 0.4);
                int tailChars = (int) (maxChars * 0.4);
                yield text.substring(0, headChars)
                        + "\n... [SUMMARIZED: Content condensed to fit token budget " + tokenBudget + "] ...\n"
                        + text.substring(text.length() - tailChars);
            }
            case NONE -> text;
        };
    }
}
