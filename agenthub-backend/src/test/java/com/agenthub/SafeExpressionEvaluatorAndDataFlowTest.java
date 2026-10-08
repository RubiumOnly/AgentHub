package com.agenthub;

import com.agenthub.orchestration.domain.evaluation.SafeExpressionEvaluator;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeExpressionEvaluatorAndDataFlowTest {

    private SafeExpressionEvaluator evaluator;
    private WorkflowExecutionContext context;

    @BeforeEach
    void setUp() {
        evaluator = new SafeExpressionEvaluator();
        context = new WorkflowExecutionContext(Map.of(
                "taskName", "用户中心开发",
                "defaultTarget", "prod"
        ));
    }

    @Test
    @DisplayName("测试上下游数据流显式传递：通过 {{steps.node.outputs.result}} 提取前置节点纯文本产物")
    void shouldResolveTextOutputsFromPredecessor() {
        context.recordNodeOutput("codegen", "public class UserService { public void login() {} }");

        String template = "请审查以下代码:\n{{steps.codegen.outputs.result}}";
        Object resolved = evaluator.resolveTemplate(template, context);

        assertThat(resolved).isEqualTo("请审查以下代码:\npublic class UserService { public void login() {} }");
    }

    @Test
    @DisplayName("测试结构化 JSON 输出安全提取：解析子属性如 score, verdict, passed")
    void shouldResolveStructuredJsonOutputs() {
        String jsonOutput = "{\"score\": 95, \"verdict\": \"APPROVED\", \"passed\": true}";
        context.recordNodeOutput("qa_audit", jsonOutput);

        Object score = context.resolvePath("steps.qa_audit.outputs.score");
        Object verdict = context.resolvePath("steps.qa_audit.outputs.verdict");
        Object passed = context.resolvePath("steps.qa_audit.outputs.passed");

        assertThat(score).isEqualTo(95);
        assertThat(verdict).isEqualTo("APPROVED");
        assertThat(passed).isEqualTo(true);
    }

    @Test
    @DisplayName("测试 inputs Map 结构化递归模板替换")
    void shouldResolveInputsMapRecursively() {
        context.recordNodeOutput("codegen", "const App = () => <div>Hello</div>;");

        Map<String, Object> inputDefs = Map.of(
                "sourceCode", "{{steps.codegen.outputs.result}}",
                "task", "优化 {{inputs.taskName}}",
                "targetEnv", "{{inputs.defaultTarget}}"
        );

        Map<String, Object> resolvedInputs = evaluator.resolveInputs(inputDefs, context);

        assertThat(resolvedInputs.get("sourceCode")).isEqualTo("const App = () => <div>Hello</div>;");
        assertThat(resolvedInputs.get("task")).isEqualTo("优化 用户中心开发");
        assertThat(resolvedInputs.get("targetEnv")).isEqualTo("prod");
    }

    @Test
    @DisplayName("测试条件分支表达式求值：数值对比、布尔运算、状态核验")
    void shouldEvaluateComplexConditionsAccurately() {
        context.recordNodeOutput("qa_audit", "{\"score\": 88, \"passed\": true, \"vulnerabilities\": 0}");
        context.getNodeState("backend").setStatus("SUCCEEDED");
        context.getNodeState("frontend").setStatus("SUCCEEDED");

        // 1. Numerical comparison
        assertThat(evaluator.evaluateCondition("steps.qa_audit.outputs.score >= 80", context)).isTrue();
        assertThat(evaluator.evaluateCondition("steps.qa_audit.outputs.score < 50", context)).isFalse();

        // 2. Status equality check
        assertThat(evaluator.evaluateCondition("steps.backend.status == 'SUCCEEDED'", context)).isTrue();
        assertThat(evaluator.evaluateCondition("steps.backend.status != 'FAILED'", context)).isTrue();

        // 3. Logical AND / OR / NOT
        assertThat(evaluator.evaluateCondition(
                "steps.backend.status == 'SUCCEEDED' && steps.frontend.status == 'SUCCEEDED'", context)).isTrue();

        assertThat(evaluator.evaluateCondition(
                "steps.qa_audit.outputs.score > 90 || steps.qa_audit.outputs.passed == true", context)).isTrue();

        assertThat(evaluator.evaluateCondition(
                "!steps.qa_audit.outputs.blocked", context)).isTrue();

        // 4. Wrapping in {{ ... }} tolerated gracefully
        assertThat(evaluator.evaluateCondition("{{steps.qa_audit.outputs.score >= 80}}", context)).isTrue();
    }

    @Test
    @DisplayName("测试空值与缺失路径防御：不抛异常且安全降级为 null/false")
    void shouldHandleNonExistentPathsGracefully() {
        Object val = context.resolvePath("steps.ghost_node.outputs.secret");
        assertThat(val).isNull();

        assertThat(evaluator.evaluateCondition("steps.ghost_node.outputs.passed == true", context)).isFalse();
        assertThat(evaluator.evaluateCondition("steps.ghost_node.outputs.secret == null", context)).isTrue();
    }

    @Test
    @DisplayName("测试安全防护：杜绝 SpEL 远程代码执行注入，任何恶意代码字符串仅作为字面量求值")
    void shouldSafelyHandleMaliciousInputStrings() {
        String maliciousPayload = "T(java.lang.Runtime).getRuntime().exec('calc.exe')";
        context.recordNodeOutput("untrusted_agent", maliciousPayload);

        // Resolving template treats payload as harmless plain text
        Object resolved = evaluator.resolveTemplate("输出内容: {{steps.untrusted_agent.outputs.result}}", context);
        assertThat(resolved).isEqualTo("输出内容: T(java.lang.Runtime).getRuntime().exec('calc.exe')");

        // Malicious expression in condition evaluates safely without executing
        boolean condResult = evaluator.evaluateCondition("steps.untrusted_agent.outputs.result == 'something_else'", context);
        assertThat(condResult).isFalse();
    }
}
