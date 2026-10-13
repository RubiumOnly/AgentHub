package com.agenthub.orchestration.domain.template;

import com.agenthub.orchestration.domain.dsl.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowTemplateRegistry {

    private final Map<String, WorkflowTemplateView> templates = new ConcurrentHashMap<>();

    public WorkflowTemplateRegistry() {
        registerCanonicalTemplates();
    }

    private void registerCanonicalTemplates() {
        // 1. Template: 修复一个带测试的后端缺陷 (BUG_FIX_WITH_TESTS)
        WorkflowDsl bugFixDsl = new WorkflowDsl("wf-tpl-bug-fix-with-tests", "修复一个带测试的后端缺陷 DSL");
        bugFixDsl.setDescription("7 阶段受控缺陷修复拓扑：需求澄清 -> 计划生成 -> 缺陷修复 -> 测试防御 -> Diff审查 -> 审批 -> 交付");
        bugFixDsl.setVersion("1.0.0");
        bugFixDsl.setEntryNodeId("node-clarify");

        List<WorkflowNodeDsl> bugFixNodes = new ArrayList<>();

        WorkflowNodeDsl nClarify = new WorkflowNodeDsl("node-clarify", "需求澄清与缺陷定位", "AGENT");
        nClarify.setAgentPlatform("PROVIDER_DRIVEN");
        nClarify.setAgentDefinitionKey("backend-architect");
        nClarify.setStage("REQUIREMENT_CLARIFICATION");
        nClarify.setPromptTemplate("分析缺陷复现堆栈与上下文，明确受影响边界与断言条件: {{inputs.requirement}}");
        nClarify.setRequiredTools(List.of("file_read", "file_tree"));
        bugFixNodes.add(nClarify);

        WorkflowNodeDsl nPlan = new WorkflowNodeDsl("node-plan", "Orchestrator 变更计划生成", "AGENT");
        nPlan.setAgentPlatform("PROVIDER_DRIVEN");
        nPlan.setAgentDefinitionKey("backend-architect");
        nPlan.setStage("PLAN");
        nPlan.setPromptTemplate("根据澄清结论生成结构化修复计划，列明目标文件、工具权限与测试策略");
        nPlan.setRequiredTools(List.of("file_read", "code_analyze"));
        bugFixNodes.add(nPlan);

        WorkflowNodeDsl nImpl = new WorkflowNodeDsl("node-impl", "后端缺陷代码修复", "AGENT");
        nImpl.setAgentPlatform("PROVIDER_DRIVEN");
        nImpl.setAgentDefinitionKey("backend-developer");
        nImpl.setStage("IMPLEMENTATION");
        nImpl.setPromptTemplate("严格遵循 Clean Code 与守卫从句规范，修复指定缺陷代码并保留原有编码风格");
        nImpl.setRequiredTools(List.of("file_read", "file_write"));
        bugFixNodes.add(nImpl);

        WorkflowNodeDsl nTest = new WorkflowNodeDsl("node-test", "编写防御性测试用例", "AGENT");
        nTest.setAgentPlatform("PROVIDER_DRIVEN");
        nTest.setAgentDefinitionKey("qa-tester");
        nTest.setStage("TEST");
        nTest.setPromptTemplate("基于五维测试矩阵编写覆盖极值、并发与空指针防御的回归测试用例");
        nTest.setRequiredTools(List.of("file_read", "file_write", "command_exec"));
        bugFixNodes.add(nTest);

        WorkflowNodeDsl nReview = new WorkflowNodeDsl("node-review", "JGit Diff 与安全质量审查", "AGENT");
        nReview.setAgentPlatform("PROVIDER_DRIVEN");
        nReview.setAgentDefinitionKey("security-reviewer");
        nReview.setStage("REVIEW");
        nReview.setPromptTemplate("审查 Git diff 变更范围、依赖安全与质量门禁结果，输出审查建议");
        nReview.setRequiredTools(List.of("file_read", "git_operation", "code_analyze"));
        bugFixNodes.add(nReview);

        WorkflowNodeDsl nApproval = new WorkflowNodeDsl("node-approval", "变更合并人工审批决定", "APPROVAL");
        nApproval.setStage("APPROVAL");
        nApproval.setRequiresApproval(true);
        bugFixNodes.add(nApproval);

        WorkflowNodeDsl nDelivery = new WorkflowNodeDsl("node-delivery", "生成受控交付快照与证据包", "AGENT");
        nDelivery.setAgentPlatform("PROVIDER_DRIVEN");
        nDelivery.setAgentDefinitionKey("backend-architect");
        nDelivery.setStage("DELIVERY");
        nDelivery.setPromptTemplate("固化 JGit commit 快照，生成不可篡改的 Run 交付证据");
        nDelivery.setRequiredTools(List.of("file_read", "git_operation"));
        bugFixNodes.add(nDelivery);

        bugFixDsl.setNodes(bugFixNodes);

        List<WorkflowEdgeDsl> bugFixEdges = List.of(
                new WorkflowEdgeDsl("node-clarify", "node-plan"),
                new WorkflowEdgeDsl("node-plan", "node-impl"),
                new WorkflowEdgeDsl("node-impl", "node-test"),
                new WorkflowEdgeDsl("node-test", "node-review"),
                new WorkflowEdgeDsl("node-review", "node-approval"),
                new WorkflowEdgeDsl("node-approval", "node-delivery")
        );
        bugFixDsl.setEdges(bugFixEdges);
        WorkflowDslValidator.validate(bugFixDsl);

        register(new WorkflowTemplateView(
                "tpl-bug-fix-with-tests",
                WorkflowTemplateType.BUG_FIX_WITH_TESTS,
                WorkflowTemplateType.BUG_FIX_WITH_TESTS.getTitle(),
                WorkflowTemplateType.BUG_FIX_WITH_TESTS.getDescription(),
                List.of("REQUIREMENT_CLARIFICATION", "PLAN", "IMPLEMENTATION", "TEST", "REVIEW", "APPROVAL", "DELIVERY"),
                List.of("backend-architect", "backend-developer", "qa-tester", "security-reviewer"),
                bugFixDsl
        ));

        // 2. Template: 为现有 Spring Boot 服务新增一个小接口并更新测试 (NEW_FEATURE_ENDPOINT)
        WorkflowDsl newEndpointDsl = new WorkflowDsl("wf-tpl-new-feature-endpoint", "为现有 Spring Boot 服务新增一个小接口并更新测试 DSL");
        newEndpointDsl.setDescription("7 阶段受控特性新增拓扑：需求澄清 -> 计划生成 -> 接口与契约编写 -> 单元与集成测试 -> 契约审查 -> 审批 -> 交付");
        newEndpointDsl.setVersion("1.0.0");
        newEndpointDsl.setEntryNodeId("node-spec");

        List<WorkflowNodeDsl> newEndpointNodes = new ArrayList<>();

        WorkflowNodeDsl nSpec = new WorkflowNodeDsl("node-spec", "REST 契约规范与参数澄清", "AGENT");
        nSpec.setAgentPlatform("PROVIDER_DRIVEN");
        nSpec.setAgentDefinitionKey("backend-architect");
        nSpec.setStage("REQUIREMENT_CLARIFICATION");
        nSpec.setPromptTemplate("根据业务诉求定义 RESTful URI、请求/响应 DTO 契约与 HTTP 状态码: {{inputs.requirement}}");
        nSpec.setRequiredTools(List.of("file_read", "file_tree"));
        newEndpointNodes.add(nSpec);

        WorkflowNodeDsl nEndpointPlan = new WorkflowNodeDsl("node-plan", "Orchestrator 契约实现计划生成", "AGENT");
        nEndpointPlan.setAgentPlatform("PROVIDER_DRIVEN");
        nEndpointPlan.setAgentDefinitionKey("backend-architect");
        nEndpointPlan.setStage("PLAN");
        nEndpointPlan.setPromptTemplate("生成分层契约计划，确认 Controller、ApplicationService 与 DTO 边界");
        nEndpointPlan.setRequiredTools(List.of("file_read", "code_analyze"));
        newEndpointNodes.add(nEndpointPlan);

        WorkflowNodeDsl nEndpointImpl = new WorkflowNodeDsl("node-impl", "编写 Controller 与应用服务实现", "AGENT");
        nEndpointImpl.setAgentPlatform("PROVIDER_DRIVEN");
        nEndpointImpl.setAgentDefinitionKey("backend-developer");
        nEndpointImpl.setStage("IMPLEMENTATION");
        nEndpointImpl.setPromptTemplate("实现 REST 端点协议转换与用例编排，保持 Controller 纯净不触碰底层持久化");
        nEndpointImpl.setRequiredTools(List.of("file_read", "file_write"));
        newEndpointNodes.add(nEndpointImpl);

        WorkflowNodeDsl nEndpointTest = new WorkflowNodeDsl("node-test", "编写 MockMvc 契约与单元测试", "AGENT");
        nEndpointTest.setAgentPlatform("PROVIDER_DRIVEN");
        nEndpointTest.setAgentDefinitionKey("qa-tester");
        nEndpointTest.setStage("TEST");
        nEndpointTest.setPromptTemplate("使用 MockMvc 编写覆盖 200/400/401/404 及分页排序契约的黑盒测试用例");
        nEndpointTest.setRequiredTools(List.of("file_read", "file_write", "command_exec"));
        newEndpointNodes.add(nEndpointTest);

        WorkflowNodeDsl nEndpointReview = new WorkflowNodeDsl("node-review", "OpenAPI 与架构规约审查", "AGENT");
        nEndpointReview.setAgentPlatform("PROVIDER_DRIVEN");
        nEndpointReview.setAgentDefinitionKey("security-reviewer");
        nEndpointReview.setStage("REVIEW");
        nEndpointReview.setPromptTemplate("校验 OpenAPI 契约对齐与 ArchUnit 架构守卫规则");
        nEndpointReview.setRequiredTools(List.of("file_read", "git_operation", "code_analyze"));
        newEndpointNodes.add(nEndpointReview);

        WorkflowNodeDsl nEndpointApproval = new WorkflowNodeDsl("node-approval", "新特性上线人工审批决定", "APPROVAL");
        nEndpointApproval.setStage("APPROVAL");
        nEndpointApproval.setRequiresApproval(true);
        newEndpointNodes.add(nEndpointApproval);

        WorkflowNodeDsl nEndpointDelivery = new WorkflowNodeDsl("node-delivery", "生成受控交付快照与证据包", "AGENT");
        nEndpointDelivery.setAgentPlatform("PROVIDER_DRIVEN");
        nEndpointDelivery.setAgentDefinitionKey("backend-architect");
        nEndpointDelivery.setStage("DELIVERY");
        nEndpointDelivery.setPromptTemplate("固化 JGit commit 快照与接口变更摘要");
        nEndpointDelivery.setRequiredTools(List.of("file_read", "git_operation"));
        newEndpointNodes.add(nEndpointDelivery);

        newEndpointDsl.setNodes(newEndpointNodes);

        List<WorkflowEdgeDsl> newEndpointEdges = List.of(
                new WorkflowEdgeDsl("node-spec", "node-plan"),
                new WorkflowEdgeDsl("node-plan", "node-impl"),
                new WorkflowEdgeDsl("node-impl", "node-test"),
                new WorkflowEdgeDsl("node-test", "node-review"),
                new WorkflowEdgeDsl("node-review", "node-approval"),
                new WorkflowEdgeDsl("node-approval", "node-delivery")
        );
        newEndpointDsl.setEdges(newEndpointEdges);
        WorkflowDslValidator.validate(newEndpointDsl);

        register(new WorkflowTemplateView(
                "tpl-new-feature-endpoint",
                WorkflowTemplateType.NEW_FEATURE_ENDPOINT,
                WorkflowTemplateType.NEW_FEATURE_ENDPOINT.getTitle(),
                WorkflowTemplateType.NEW_FEATURE_ENDPOINT.getDescription(),
                List.of("REQUIREMENT_CLARIFICATION", "PLAN", "IMPLEMENTATION", "TEST", "REVIEW", "APPROVAL", "DELIVERY"),
                List.of("backend-architect", "backend-developer", "qa-tester", "security-reviewer"),
                newEndpointDsl
        ));
    }

    public void register(WorkflowTemplateView template) {
        if (template != null && template.getTemplateId() != null) {
            templates.put(template.getTemplateId(), template);
        }
    }

    public List<WorkflowTemplateView> listTemplates() {
        return new ArrayList<>(templates.values());
    }

    public Optional<WorkflowTemplateView> getTemplate(String templateId) {
        if (templateId == null) return Optional.empty();
        return Optional.ofNullable(templates.get(templateId));
    }
}
