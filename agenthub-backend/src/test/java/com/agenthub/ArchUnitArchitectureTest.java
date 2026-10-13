package com.agenthub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchUnitArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.agenthub");
    }

    @Test
    @DisplayName("架构守护 1：所有 Controller 控制器类严禁直接注入或依赖 Repository 数据仓储层")
    void controllersShouldNotDependOnRepositories() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAnyPackage("..repository..")
                .because("Controllers must only call Application interfaces and must never access Repositories directly")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 2：所有 RestController 控制器必须位于 api 或 adapter.web 接入层包内")
    void controllersShouldResideInApiOrWebPackages() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage("..api..", "..adapter.web..")
                .because("Web Controllers must reside within api or adapter.web package layers")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 3：领域应用服务实现类必须位于 application 包内")
    void applicationServicesShouldResideInApplicationPackages() {
        classes().that().haveSimpleNameEndingWith("ApplicationService")
                .should().resideInAPackage("..application..")
                .because("Application Services orchestrating use cases must reside within application packages")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 4：所有 RestController 控制器严禁直接依赖或暴露 JPA 实体 Entity 类")
    void controllersShouldNotDependOnEntities() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAnyPackage("..entity..")
                .because("Controllers must only exchange command/query/view DTOs and must never leak JPA entities")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 5（Phase 0 瘦身冻结）：废弃的旧版控制器与原型调度服务严禁出现新的生产调用方")
    void deprecatedLegacyComponentsShouldNotGainNewCallers() {
        noClasses().that().resideOutsideOfPackage("com.agenthub.adapter.web..")
                .should().dependOnClassesThat().resideInAPackage("com.agenthub.adapter.web..")
                .because("Legacy com.agenthub.adapter.web controllers and inner DTOs are frozen for Phase 1 convergence and must not gain external callers")
                .check(importedClasses);

        noClasses().that().doNotHaveFullyQualifiedName("com.agenthub.execution.api.RunAliasController")
                .should().dependOnClassesThat().haveFullyQualifiedName("com.agenthub.execution.api.RunAliasController")
                .because("Compatibility alias RunAliasController is frozen and must not be depended on by other classes")
                .check(importedClasses);

        noClasses().that().resideOutsideOfPackages(
                        "com.agenthub.domain.workflow..",
                        "com.agenthub.adapter.web.."
                )
                .and().doNotHaveFullyQualifiedName("com.agenthub.project.application.WorkspaceApplication")
                .and().doNotHaveFullyQualifiedName("com.agenthub.project.application.WorkspaceApplicationService")
                .should().dependOnClassesThat().resideInAPackage("com.agenthub.domain.workflow..")
                .because("Deprecated prototype WorkflowEngineService and WorkflowModels are frozen and must only be referenced by legacy migration adapters")
                .check(importedClasses);

        noClasses().that().haveNameNotMatching("com\\.agenthub\\.execution\\.scheduler\\.ExecutionScheduler(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching("com\\.agenthub\\.execution\\.scheduler\\.ExecutionScheduler(\\$.*)?")
                .because("Deprecated linear ExecutionScheduler is frozen in favor of DagExecutionEngine and must have zero external callers")
                .check(importedClasses);

        noClasses().that().haveNameNotMatching("com\\.agenthub\\.application\\.service\\.IMCollaborationService(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching("com\\.agenthub\\.application\\.service\\.IMCollaborationService(\\$.*)?")
                .because("Legacy IMCollaborationService prototype is frozen for removal and must not gain new callers")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 6：所有领域层（..domain..）严禁反向依赖 Web 控制器（@RestController）")
    void domainLayerShouldNotDependOnWebControllers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .because("Domain models and services must remain independent of HTTP/Web RestControllers")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 7（Phase 0 适配器冻结）：早期原型 UnifiedAgentAdapter 与 infrastructure.adapter 严禁新增调用方")
    void prototypeAdaptersShouldNotGainNewCallers() {
        noClasses().that().resideOutsideOfPackage("com.agenthub.infrastructure.adapter..")
                .and().doNotHaveFullyQualifiedName("com.agenthub.runtime.cli.CliAgentRuntime")
                .and().doNotHaveFullyQualifiedName("com.agenthub.runtime.openai.OpenAiCompatibleRuntime")
                .should().dependOnClassesThat().resideInAPackage("com.agenthub.infrastructure.adapter..")
                .because("Legacy prototype adapters in com.agenthub.infrastructure.adapter are frozen for Phase 1 retirement")
                .check(importedClasses);

        noClasses().that().resideOutsideOfPackages(
                        "com.agenthub.domain.agent..",
                        "com.agenthub.infrastructure.adapter..",
                        "com.agenthub.domain.workflow.."
                )
                .and().doNotHaveFullyQualifiedName("com.agenthub.agent.application.AgentApplicationService")
                .and().doNotHaveFullyQualifiedName("com.agenthub.conversation.application.ConversationApplicationService")
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.agenthub\\.domain\\.agent\\.(service\\.AgentAdapterFactory|spi\\.UnifiedAgentAdapter)(\\$.*)?"
                )
                .because("Legacy AgentAdapterFactory and UnifiedAgentAdapter are frozen in favor of AgentRuntimeRegistry + LlmProvider SPI")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 8（Phase 0 禁止新增 Mock Fallback）：生产 API 控制器与应用服务严禁直接依赖 Mock 运行时或 Mock Provider")
    void productionApiAndApplicationLayersShouldNotDependOnMockImplementations() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .or().resideInAPackage("..application..")
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.agenthub\\.(runtime\\.mock\\.MockAgentRuntime|agent\\.infrastructure\\.provider\\.MockLlmProvider)(\\$.*)?"
                )
                .because("Production API Controllers and Application Services must never hardcode or fallback directly to MockAgentRuntime or MockLlmProvider")
                .check(importedClasses);

        noClasses().that().haveNameNotMatching("com\\.agenthub\\.runtime\\.mock\\.MockAgentRuntime(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching("com\\.agenthub\\.runtime\\.mock\\.MockAgentRuntime(\\$.*)?")
                .because("MockAgentRuntime must only be resolved dynamically via AgentRuntimeRegistry when explicitly requested")
                .check(importedClasses);

        noClasses().that().haveNameNotMatching("com\\.agenthub\\.agent\\.infrastructure\\.provider\\.MockLlmProvider(\\$.*)?")
                .and().haveNameNotMatching("com\\.agenthub\\.agent\\.domain\\.provider\\.router\\.DynamicProviderRouter(\\$.*)?")
                .should().dependOnClassesThat().haveNameMatching("com\\.agenthub\\.agent\\.infrastructure\\.provider\\.MockLlmProvider(\\$.*)?")
                .because("MockLlmProvider must not gain new callers outside DynamicProviderRouter")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 9（Phase 1 控制器纯净度）：所有 RestController 严禁直接操作 java.nio.file.Path、JGit 或底层工作区锁")
    void controllersShouldNotManipulateHostPathsOrJGitDirectly() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Path")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.agenthub.domain.workspace.service.JGitWorkspaceManager")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.agenthub.domain.workspace.service.WorkspaceResolver")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.agenthub.infrastructure.concurrency.WorkspaceLockManager")
                .because("Controllers must only exchange DTOs with Application services and never manipulate host file paths, JGit, or locks directly")
                .check(importedClasses);
    }

    @Test
    @DisplayName("架构守护 10（Phase 1 遗留控制器隔离）：所有旧版兼容控制器必须同时标注 @Deprecated 与 @Profile(\"!prod\")")
    void legacyControllersMustBeDeprecatedAndExcludedFromProdProfile() {
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage("com.agenthub.adapter.web..")
                .or().haveFullyQualifiedName("com.agenthub.execution.api.RunAliasController")
                .should().beAnnotatedWith(Deprecated.class)
                .andShould().beAnnotatedWith(org.springframework.context.annotation.Profile.class)
                .because("Legacy compatibility controllers must be marked @Deprecated and @Profile(\"!prod\") so production profile registers zero legacy controllers")
                .check(importedClasses);
    }
}
