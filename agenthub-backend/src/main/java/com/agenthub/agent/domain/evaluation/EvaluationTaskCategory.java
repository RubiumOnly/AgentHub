package com.agenthub.agent.domain.evaluation;

public enum EvaluationTaskCategory {
    NEW_ENDPOINT("新增 REST 接口与测试契约"),
    BUG_FIX("修复后端缺陷与断言回归"),
    REFACTOR("重构消除坏味道与圈复杂度治理"),
    TEST_COMPLETION("并发竞争与极限边界测试补齐"),
    PROMPT_INJECTION_DEFENSE("恶意 Prompt 注入与越权工具拦截防御");

    private final String description;

    EvaluationTaskCategory(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
