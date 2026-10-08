package com.agenthub;

import com.agenthub.orchestration.domain.dsl.*;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDslValidationAndCycleDetectionTest {

    @Test
    @DisplayName("测试标准线性 DAG 拓扑排序成功 (START -> Agent1 -> Agent2 -> END)")
    void shouldValidateLinearDagSuccessfully() {
        WorkflowDsl dsl = new WorkflowDsl("wf-linear", "线性工作流");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "开始节点", "START"),
                new WorkflowNodeDsl("backend", "后端生成", "AGENT", "MOCK", "生成接口"),
                new WorkflowNodeDsl("qa", "质量审计", "AGENT", "MOCK", "单元测试"),
                new WorkflowNodeDsl("end", "结束节点", "END")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "backend"),
                new WorkflowEdgeDsl("backend", "qa"),
                new WorkflowEdgeDsl("qa", "end")
        ));

        TopologicalSortResult result = WorkflowDslValidator.validate(dsl);
        assertThat(result.getSortedNodeIds()).containsExactly("start", "backend", "qa", "end");
    }

    @Test
    @DisplayName("测试菱形分支并发 DAG 拓扑排序 (START -> [Backend, Frontend] -> QA -> END)")
    void shouldValidateDiamondDagSuccessfully() {
        WorkflowDsl dsl = new WorkflowDsl("wf-diamond", "菱形工作流");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "开始", "START"),
                new WorkflowNodeDsl("backend", "后端", "AGENT", "MOCK", "代码"),
                new WorkflowNodeDsl("frontend", "前端", "AGENT", "MOCK", "组件"),
                new WorkflowNodeDsl("qa", "QA聚合", "AGENT", "MOCK", "审查"),
                new WorkflowNodeDsl("end", "结束", "END")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "backend"),
                new WorkflowEdgeDsl("start", "frontend"),
                new WorkflowEdgeDsl("backend", "qa"),
                new WorkflowEdgeDsl("frontend", "qa"),
                new WorkflowEdgeDsl("qa", "end")
        ));

        TopologicalSortResult result = WorkflowDslValidator.validate(dsl);
        assertThat(result.getSortedNodeIds()).startsWith("start");
        assertThat(result.getSortedNodeIds()).endsWith("end");
        assertThat(result.getSortedNodeIds().indexOf("qa"))
                .isGreaterThan(result.getSortedNodeIds().indexOf("backend"))
                .isGreaterThan(result.getSortedNodeIds().indexOf("frontend"));
    }

    @Test
    @DisplayName("测试直接环路依赖死锁检测 (A -> B -> A) 抛出 6001 错误码")
    void shouldDetectDirect2NodeCycle() {
        WorkflowDsl dsl = new WorkflowDsl("wf-cycle-2", "直接死锁环路");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("nodeA", "节点A", "AGENT"),
                new WorkflowNodeDsl("nodeB", "节点B", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("nodeA", "nodeB"),
                new WorkflowEdgeDsl("nodeB", "nodeA")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Cycle detected in workflow graph");
                });
    }

    @Test
    @DisplayName("测试长程三节点环路依赖死锁检测 (A -> B -> C -> A) 抛出 6001 错误码")
    void shouldDetect3NodeCycle() {
        WorkflowDsl dsl = new WorkflowDsl("wf-cycle-3", "长程死锁环路");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "入口", "START"),
                new WorkflowNodeDsl("nodeA", "节点A", "AGENT"),
                new WorkflowNodeDsl("nodeB", "节点B", "AGENT"),
                new WorkflowNodeDsl("nodeC", "节点C", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "nodeA"),
                new WorkflowEdgeDsl("nodeA", "nodeB"),
                new WorkflowEdgeDsl("nodeB", "nodeC"),
                new WorkflowEdgeDsl("nodeC", "nodeA")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Cycle detected");
                });
    }

    @Test
    @DisplayName("测试自环依赖 (A -> A) 抛出 6001 错误码")
    void shouldDetectSelfLoop() {
        WorkflowDsl dsl = new WorkflowDsl("wf-self-loop", "自环");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("nodeA", "自环节点", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("nodeA", "nodeA")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Self-loop edge detected");
                });
    }

    @Test
    @DisplayName("测试孤岛节点检测 (多节点下存在无任何连线的孤岛节点) 抛出 6001 错误码")
    void shouldDetectIsolatedIslandNode() {
        WorkflowDsl dsl = new WorkflowDsl("wf-island", "孤岛图");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("nodeA", "节点A", "AGENT"),
                new WorkflowNodeDsl("nodeB", "节点B", "AGENT"),
                new WorkflowNodeDsl("islandNode", "孤岛节点", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("nodeA", "nodeB")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Isolated island node detected");
                });
    }

    @Test
    @DisplayName("测试重复节点 ID 检测抛出 6001 错误码")
    void shouldDetectDuplicateNodeId() {
        WorkflowDsl dsl = new WorkflowDsl("wf-dup", "重复节点");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("same-id", "节点1", "AGENT"),
                new WorkflowNodeDsl("same-id", "节点2", "AGENT")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Duplicate node ID");
                });
    }

    @Test
    @DisplayName("测试悬空连线目标不存在检测抛出 6001 错误码")
    void shouldDetectMissingEdgeTarget() {
        WorkflowDsl dsl = new WorkflowDsl("wf-dangling", "悬空边");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("nodeA", "节点A", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("nodeA", "non-existent")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Edge target node not found");
                });
    }

    @Test
    @DisplayName("测试非法负数超时配置拦截")
    void shouldRejectNegativeTimeout() {
        WorkflowDsl dsl = new WorkflowDsl("wf-negative-timeout", "负数超时");
        WorkflowNodeDsl node = new WorkflowNodeDsl("node1", "节点1", "AGENT");
        node.setTimeoutSeconds(-10L);
        dsl.setNodes(List.of(node));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(dsl))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(be.getMessage()).contains("Node timeout must not be negative");
                });
    }

    @Test
    @DisplayName("测试 Kahn 拓扑排序后保留真实图入度信息，避免结果入度被算法置零")
    void shouldPreserveInitialInDegreesInTopologicalSortResult() {
        WorkflowDsl dsl = new WorkflowDsl("wf-diamond-indegrees", "菱形拓扑入度验证");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "开始", "START"),
                new WorkflowNodeDsl("branchA", "分支A", "AGENT"),
                new WorkflowNodeDsl("branchB", "分支B", "AGENT"),
                new WorkflowNodeDsl("join", "汇聚", "AGENT"),
                new WorkflowNodeDsl("end", "结束", "END")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "branchA"),
                new WorkflowEdgeDsl("start", "branchB"),
                new WorkflowEdgeDsl("branchA", "join"),
                new WorkflowEdgeDsl("branchB", "join"),
                new WorkflowEdgeDsl("join", "end")
        ));

        TopologicalSortResult result = WorkflowDslValidator.validate(dsl);
        assertThat(result.getInDegrees().get("start")).isEqualTo(0);
        assertThat(result.getInDegrees().get("branchA")).isEqualTo(1);
        assertThat(result.getInDegrees().get("branchB")).isEqualTo(1);
        assertThat(result.getInDegrees().get("join")).isEqualTo(2);
        assertThat(result.getInDegrees().get("end")).isEqualTo(1);
    }
}
