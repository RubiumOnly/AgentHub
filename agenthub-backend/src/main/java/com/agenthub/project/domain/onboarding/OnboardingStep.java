package com.agenthub.project.domain.onboarding;

/**
 * Seven-step Onboarding Wizard Lifecycle for controlled project onboarding.
 */
public enum OnboardingStep {
    CONNECT_SOURCE(1, "连接来源 (Connect Source)"),
    VERIFY_PERMISSIONS(2, "验证权限 (Verify Permissions)"),
    SELECT_DEFAULT_BRANCH(3, "选择默认分支 (Select Default Branch)"),
    CREATE_WORKSPACE(4, "创建受控工作区 (Create Controlled Workspace)"),
    SCAN_ENVIRONMENT(5, "扫描环境与技术栈 (Scan Environment)"),
    GENERATE_PROJECT_RULES(6, "生成项目规则草稿 (Generate Project Rules)"),
    CREATE_INITIAL_BASELINE(7, "创建初始 Git 基线 (Create Initial Baseline)");

    private final int sequence;
    private final String description;

    OnboardingStep(int sequence, String description) {
        this.sequence = sequence;
        this.description = description;
    }

    public int getSequence() { return sequence; }
    public String getDescription() { return description; }

    public OnboardingStep next() {
        OnboardingStep[] values = values();
        for (int i = 0; i < values.length - 1; i++) {
            if (values[i] == this) {
                return values[i + 1];
            }
        }
        return null;
    }
}
