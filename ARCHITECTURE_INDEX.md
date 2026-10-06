# AOCI Codebase Architecture Index: AgentHub
# Schema: aoci-spec/v1.0
# BaseStack: Java 17 LTS | Spring Boot 3.3.4 | Next.js 14 | JGit 6.8 | Docker | Maven 3.9

## 1. System Topology & Layers
[SYS.ARCH]
ROOT: d:\work\agenthub
BACKEND: agenthub-backend/ (Spring Boot 3.3, DDD 4-layer, Port 8080)
FRONTEND: agenthub-frontend/ (Next.js 14, Tailwind, Bento Grid, Port 3000)
DATA_WORKSPACES: d:\work\agenthub\data\workspaces\

[DDD.BOUNDARIES]
- Adapter Layer (com.agenthub.adapter):
  - web: SystemHealthController, AgentRegistryController, IMController, WorkflowAndDiffController, SandboxController
  - common: Result<T>, ErrorCode, BusinessException, GlobalExceptionHandler
- Application Layer (com.agenthub.application):
  - IMCollaborationService: Conversation lifecycle, SSE emitter streaming, mention dispatch
- Domain Layer (com.agenthub.domain):
  - domain.agent: AgentPlatformType, AgentCapability, AgentExecutionRequest, AgentExecutionResult, UnifiedAgentAdapter SPI, AgentAdapterFactory
  - domain.conversation: ConversationType, SenderType, InteractiveCard, MentionParser, OrchestratorTaskDecomposer
  - domain.workflow: WorkflowModels (NodeType, ExecutionStatus, NodeDefinition, WorkflowDefinition, NodeRunState, WorkflowExecutionResult), WorkflowEngineService
  - domain.workspace: FileDiffEntry, JGitWorkspaceManager
  - domain.sandbox: DeploymentManifest, PreviewSandboxService
- Infrastructure Layer (com.agenthub.infrastructure):
  - adapter: CliProcessAdapter (Windows/.cmd/secret-redaction/graceful-fallback), OpenClawCliAdapter, CodexCliAdapter, SpringAiApiAdapter
  - concurrency: WorkspaceLockManager (thread-safe ReentrantLock with timeout)
  - repository: ConversationRepository, MessageRepository, ConversationEntity, MessageEntity
  - config: CorsConfig

## 2. Verification & Test Metrics
- Backend Tests: 13 / 13 Passed (100% Green, 0 Failures, 0 Errors)
  - SystemHealthTest (1 test)
  - AgentAdapterTest (4 tests)
  - IMCollaborationTest (3 tests)
  - WorkflowAndJGitTest (3 tests)
  - SandboxAndDeployTest (2 tests)
- Frontend Build: Next.js 14 Compiled Successfully (Static pages generated, 0 Type Errors)

## 3. Module Inventory & Status
| Module | Path | Responsibilities | Status |
| :--- | :--- | :--- | :--- |
| Core.Config | com.agenthub.infrastructure.config | CORS, Jackson, UTF-8 Encoding | Verified |
| Domain.Agent | com.agenthub.domain.agent | UnifiedAgentAdapter SPI & Models | Verified |
| Infra.Adapter | com.agenthub.infrastructure.adapter | CLI Process (Claude/Codex/OpenClaw) & API Adapters | Verified |
| Domain.IM | com.agenthub.domain.conversation | Feishu-like IM, @Mention, InteractiveCards, SSE | Verified |
| Domain.Workflow| com.agenthub.domain.workflow | DAG State Machine & Task Runner | Verified |
| Domain.GitDiff | com.agenthub.domain.workspace | JGit Native Code Diff Engine | Verified |
| Infra.Sandbox | com.agenthub.infrastructure.sandbox | Web Preview Sandbox & One-Click Deploy | Verified |
| Frontend.UI | agenthub-frontend | Next.js 14 Bento Grid Workspace | Verified |
