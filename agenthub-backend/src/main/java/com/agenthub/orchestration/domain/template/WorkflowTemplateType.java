package com.agenthub.orchestration.domain.template;

public enum WorkflowTemplateType {
    BUG_FIX_WITH_TESTS("修复一个带测试的后端缺陷", "受控修复后端缺陷并补充防御测试用例，经审查后审批交付"),
    NEW_FEATURE_ENDPOINT("为现有 Spring Boot 服务新增一个小接口并更新测试", "受控新增 REST 端点、数据契约与配套自动化测试，经审查后审批交付");

    private final String title;
    private final String description;

    WorkflowTemplateType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
