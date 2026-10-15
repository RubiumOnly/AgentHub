// ============================================================================
// AgentHub Domain & View Type Definitions
// Phase 8: Bento Executive Dashboard & Modern Aesthetic UI
// ============================================================================

export type WorkflowNodeType =
  | "START"
  | "AGENT"
  | "APPROVAL"
  | "CONDITION"
  | "JOIN"
  | "END";

export type WorkflowNodeStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED"
  | "WAITING_APPROVAL";

export type RunStatus =
  | "QUEUED"
  | "RUNNING"
  | "PAUSED"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "REJECTED";

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface WorkflowRunView {
  id: string;
  projectId: string;
  definitionId: string;
  status: RunStatus;
  idempotencyKey?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  cancelReason?: string;
  cancelledAt?: string;
  correlationId?: string;
}

export interface StepRunView {
  id: string;
  runId: string;
  nodeId: string;
  status: WorkflowNodeStatus;
  attempt: number;
  inputRef?: string;
  outputRef?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  correlationId?: string;
}

export interface ApprovalView {
  id: string;
  runId: string;
  stepRunId: string;
  status: ApprovalStatus;
  requestedBy: string;
  reviewedBy?: string;
  decisionReason?: string;
  decision?: "APPROVED" | "REJECTED";
  comments?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowDslNode {
  id: string;
  label: string;
  type: WorkflowNodeType;
  agentPlatform?: string;
  promptTemplate?: string;
  conditionExpression?: string;
  approverRole?: string;
  timeoutMs?: number;
  nextNodeIds?: string[];
  status?: WorkflowNodeStatus;
}

export interface WorkflowDefinitionView {
  id: string;
  name: string;
  version: number;
  description?: string;
  nodes: WorkflowDslNode[];
}

export type TeamTopology = "HIERARCHICAL" | "PEER_TO_PEER" | "ROUND_ROBIN";

export type TeamRole =
  | "ORCHESTRATOR"
  | "ARCHITECT"
  | "CODER"
  | "REVIEWER"
  | "TESTER";

export interface TeamMemberView {
  id: string;
  teamId: string;
  agentInstanceId: string;
  role: string;
  roleType: TeamRole;
  sortOrder: number;
  responsibilities?: string;
  systemPromptOverride?: string;
  canDelegate: boolean;
}

export interface TeamView {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  topology: TeamTopology;
  leaderAgentId?: string;
  maxTurns: number;
  configJson?: string;
  status: string;
  members: TeamMemberView[];
  createdAt?: string;
}

export type MessageType = "BROADCAST" | "DIRECT" | "SYSTEM";
export type MessageProtocolType = "NORMAL" | "REQUEST_REPLY" | "HANDOFF" | "SUMMARIZE";

export interface Subtask {
  id: string;
  targetAgent: string;
  title: string;
  status: string;
}

export interface InteractiveCard {
  cardId: string;
  headerTitle: string;
  summary: string;
  subtasks?: Subtask[];
  actions?: string[];
}

export interface MessageView {
  id: string;
  conversationId?: string;
  senderId: string;
  senderType: "USER" | "AGENT" | "ORCHESTRATOR" | "SYSTEM";
  recipientId?: string;
  messageType?: MessageType;
  protocolType?: MessageProtocolType;
  content: string;
  cardPayloadJson?: string;
  cardPayload?: InteractiveCard;
  mentions?: string;
  schemaVersion?: string;
  sequenceNum?: number;
  tokenCount?: number;
  inReplyToId?: string;
  createdAt?: string;
  timestamp?: string;
}

export interface ConversationView {
  id: string;
  title: string;
  type: string;
  projectId?: string;
  teamId?: string;
  summary?: string;
  participantAgentIds?: string;
  createdAt?: string;
}

export type DiffChangeType = "ADD" | "MODIFY" | "DELETE" | "RENAME" | "COPY";
export type ArtifactReviewStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "REVERTED" | "GATE_FAILED";

export type QualityGateStatus = "PASSED" | "FAILED" | "WARNING" | "SKIPPED";

export interface QualityGateCheckItem {
  ruleType: string;
  ruleName: string;
  status: QualityGateStatus;
  weight: number;
  message: string;
  detailJson?: string;
}

export interface QualityGateReport {
  reportId: string;
  artifactId: string;
  runId: string;
  overallStatus: QualityGateStatus;
  compositeScore: number;
  totalRules: number;
  passedRules: number;
  failedRules: number;
  warningRules: number;
  checkItems: QualityGateCheckItem[];
  sha256Fingerprint: string;
  evaluatedAt: string;
}

export interface PullRequestDraftView {
  draftId: string;
  artifactId: string;
  runId: string;
  targetBranch: string;
  title: string;
  summary: string;
  riskAssessment: string;
  qualityGateStatus: string;
  qualityGateScore: number;
  changedFiles: string[];
  totalLinesAdded: number;
  totalLinesDeleted: number;
  generatedAt: string;
}

export interface DeploymentProbeResult {
  deploymentId: string;
  status: string;
  healthy: boolean;
  httpStatusCode?: number;
  latencyMs?: number;
  message?: string;
}

export interface FileDiffEntry {
  oldPath: string;
  newPath: string;
  changeType: DiffChangeType;
  diffContent?: string;
  diffText?: string;
  linesAdded?: number;
  linesDeleted?: number;
  addedLines?: number;
  deletedLines?: number;
  binary?: boolean;
  renamed?: boolean;
  conflict?: boolean;
  reviewStatus?: ArtifactReviewStatus;
  checksum?: string;
}

export interface StructuredDiff {
  workspaceId?: string;
  workspaceName?: string;
  baseCommit?: string;
  targetCommit?: string;
  baselineCommitHash?: string;
  headCommitHash?: string;
  totalFilesChanged: number;
  totalLinesAdded: number;
  totalLinesDeleted: number;
  entries: FileDiffEntry[];
  clean?: boolean;
}

export interface WorkspaceFileNode {
  name: string;
  relativePath: string;
  isDirectory: boolean;
  size: number;
  lastModified?: string;
}

export interface ArtifactView {
  id: string;
  runId: string;
  stepRunId?: string;
  stepId?: string;
  commitHash?: string;
  artifactType?: string;
  type?: string;
  path?: string;
  pathOrRef?: string;
  checksum?: string;
  fileCount?: number;
  byteSize?: number;
  status?: "CREATED" | "ACCEPTED" | "REJECTED" | "REVERTED";
  reviewerId?: string;
  reviewReason?: string;
  metadataJson?: string;
  reviewStatus?: ArtifactReviewStatus;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewComment?: string;
  createdAt?: string;
}

export type CircuitStatus = "CLOSED" | "OPEN" | "HALF_OPEN";

export interface ProviderView {
  id: string;
  ownerId?: string;
  providerType: string;
  baseUrl?: string;
  secretRef?: string;
  model: string;
  status: string;
  priority: number;
  weight: number;
  capabilities?: string;
  costPerMillionInput: number;
  costPerMillionOutput: number;
  circuitStatus: CircuitStatus;
  avgLatencyMs: number;
  createdAt?: string;
}

export interface TokenSummaryView {
  totalPromptTokens: number;
  totalCompletionTokens: number;
  totalTokens: number;
  totalEstimatedCost: number;
  totalInvocations: number;
}

export interface RouteDecisionView {
  selectedProviderId: string;
  selectedModel: string;
  reason: string;
  candidateChain?: string[];
}

export type DeploymentStatus =
  | "CREATED"
  | "BUILD"
  | "BUILDING"
  | "START"
  | "HEALTH_CHECK"
  | "RUNNING"
  | "STOP"
  | "CLEANUP"
  | "STOPPED"
  | "FAILED";

export interface DeploymentResponse {
  id: string;
  projectId: string;
  artifactId?: string;
  status: DeploymentStatus;
  target: string;
  sandboxType: "LOCAL_PROCESS" | "DOCKER";
  port?: number;
  url?: string;
  buildCommand?: string;
  startCommand?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  errorMessage?: string;
  hasLogs: boolean;
  processId?: number;
  containerId?: string;
  workingDirectory?: string;
  logCursor?: number;
  terminationReason?: string;
  templateType?: string;
  lifecycleTrace?: string;
}

export interface CreateDeploymentRequest {
  projectId: string;
  artifactId?: string;
  templateType?: string;
  target: string;
  sandboxType: "LOCAL_PROCESS" | "DOCKER";
  buildCommand?: string;
  startCommand?: string;
  port?: number;
  healthCheckPath?: string;
  envVars?: Record<string, string>;
  timeoutMs?: number;
}

export interface RunEventView {
  id: string;
  runId: string;
  sequenceNum: number;
  eventType: string;
  payload: string;
  createdAt: string;
}

export interface UserView {
  id: string;
  email: string;
  displayName?: string;
  status?: string;
  createdAt?: string;
}

export interface AuthTokenView {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: UserView;
}

export interface ProjectView {
  id: string;
  ownerId?: string;
  name: string;
  description?: string;
  status: string;
  workspaceId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkspaceView {
  id: string;
  projectId: string;
  basePath?: string;
  relativeRoot?: string;
  status?: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface StartRunRequest {
  projectId: string;
  definitionId?: string;
  idempotencyKey?: string;
  workflowDsl?: any;
  inputs?: Record<string, any>;
}

export interface RunEvidenceBundleView {
  schemaVersion: string;
  bundleId: string;
  exportedAt: string;
  simulated: boolean;
  executionMode: "DEMO_SIMULATED" | "LIVE_PROVIDER" | string;
  project: {
    id: string;
    name: string;
    ownerId?: string;
    workspaceId?: string;
    gitBaselineCommit?: string;
  };
  workflowDslSnapshot?: string;
  run: WorkflowRunView;
  steps: StepRunView[];
  events: RunEventView[];
  providerRuntime: {
    runtimeTypes: string[];
    providerTypes: string[];
    models: string[];
    simulated: boolean;
    totalPromptTokens: number;
    totalCompletionTokens: number;
    totalTokens: number;
    totalEstimatedCost: number;
    tokenUsageRecords: any[];
  };
  gitDiff?: StructuredDiff;
  artifacts: ArtifactView[];
  testReportSummary: {
    status: string;
    totalSteps: number;
    succeededSteps: number;
    failedSteps: number;
    skippedSteps: number;
    cancelledSteps: number;
    verificationSummary: string;
    logs: string[];
  };
  approvals: ApprovalView[];
  deployments: {
    id: string;
    projectId: string;
    artifactId?: string;
    status: string;
    target: string;
    sandboxType: string;
    templateType: string;
    url?: string;
    port?: number;
    healthCheckPath?: string;
    lifecycleTrace?: string;
    terminationReason?: string;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
  }[];
  checksum: string;
}

// ============================================================================
// Phase 2: Project Onboarding, Controlled Workspace, & Versioned Context Sources
// ============================================================================

export type GitSourceType = "LOCAL_DIR" | "REMOTE_GIT";

export interface GitSourceConfig {
  sourceType: GitSourceType;
  localPath?: string;
  location?: string;
  remoteUrl?: string;
  repoUrl?: string;
  secretRef?: string;
  defaultBranch?: string;
}

export type OnboardingStep =
  | "CONNECT_SOURCE"
  | "VERIFY_PERMISSIONS"
  | "SELECT_DEFAULT_BRANCH"
  | "CREATE_WORKSPACE"
  | "SCAN_ENVIRONMENT"
  | "GENERATE_PROJECT_RULES"
  | "CREATE_INITIAL_BASELINE";

export type OnboardingSessionStatus = "IN_PROGRESS" | "COMPLETED" | "FAILED" | "ABORTED";

export type OnboardingStepStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "SKIPPED";

export interface EnvironmentScanResultView {
  language: string;
  framework: string;
  buildTool: string;
  testFramework?: string;
  testRunner?: string;
  suggestedBuildCommand?: string;
  suggestedTestCommand?: string;
  detectedConfigFiles: string[];
  detectedFiles?: string[];
  securityNotice?: string;
}

export interface OnboardingStepRecordView {
  step: OnboardingStep;
  status: OnboardingStepStatus;
  startedAt?: string;
  finishedAt?: string;
  summary?: string;
  detail?: string;
  errorMessage?: string;
}

export interface OnboardingSessionView {
  sessionId: string;
  projectId?: string;
  projectName: string;
  description?: string;
  ownerId: string;
  status: OnboardingSessionStatus;
  currentStep: OnboardingStep;
  isolatedBranch?: string;
  workspaceId?: string;
  baselineCommit?: string;
  sourceConfig: GitSourceConfig;
  scanResult?: EnvironmentScanResultView;
  generatedRules?: string;
  steps: OnboardingStepRecordView[];
  createdAt: string;
  updatedAt: string;
}

export interface WorkspaceQuotaUsage {
  workspaceId: string;
  currentCapacityBytes: number;
  maxCapacityBytes: number;
  currentFilesCount: number;
  maxFilesCount: number;
  maxSingleFileSizeBytes: number;
  currentExecutionDurationSeconds: number;
  maxExecutionDurationSeconds: number;
  allowNetworkAccess: boolean;
  activeConcurrentRuns: number;
  maxConcurrentRuns: number;
}

export type ContextSourceType = "AOCI_COGNITION" | "PROJECT_RULES" | "BUILD_CONFIG" | "TEST_CONVENTIONS";

export type ContextSourceSecurityLevel = "PUBLIC" | "INTERNAL" | "RESTRICTED" | "CONFIDENTIAL";

export interface ContextSourceView {
  id: string;
  projectId: string;
  sourceType: ContextSourceType;
  name: string;
  relativePath?: string;
  content: string;
  contentFingerprint: string;
  commitHash: string;
  securityLevel: ContextSourceSecurityLevel;
  metadata?: Record<string, string>;
  createdAt: string;
}

// ============================================================================
// Phase 3: Versioned Workflow Templates, Kahn DAG & Orchestrator Plan Types
// ============================================================================

export type WorkflowTemplateType = "BUG_FIX_WITH_TESTS" | "NEW_FEATURE_ENDPOINT";

export interface WorkflowEdgeDsl {
  fromNodeId: string;
  toNodeId: string;
}

export interface WorkflowNodeDsl {
  id: string;
  name: string;
  type: string;
  agentPlatform?: string;
  promptTemplate?: string;
  agentRole?: string;
  requiresApproval?: boolean;
  metadata?: Record<string, unknown>;
}

export interface WorkflowDsl {
  id: string;
  name: string;
  version?: number;
  description?: string;
  nodes: WorkflowNodeDsl[];
  edges: WorkflowEdgeDsl[];
}

export interface WorkflowTemplateView {
  id: string;
  name: string;
  templateType: WorkflowTemplateType;
  description: string;
  version: string;
  stages: string[];
  requiredAgentRoles: string[];
  dsl: WorkflowDsl;
}

export interface ValidateDslResultView {
  valid: boolean;
  errorMessage?: string;
  topologicalOrder: string[];
  executionStages: string[][];
  approvalNodeIds: string[];
}

export interface ToolPermissionRequest {
  toolName: string;
  category: string;
  highImpact: boolean;
  purpose: string;
  parametersHint: string;
  authorized: boolean;
}

export interface EstimatedBudget {
  maxTokens: number;
  costUsdUpperLimit: number;
  timeoutSeconds: number;
}

export type OrchestrationPlanStatus = "GENERATED" | "CONFIRMED" | "REJECTED";

export interface OrchestrationPlanView {
  planId: string;
  projectId: string;
  workspaceId: string;
  taskGoal: string;
  requirement?: string;
  templateType: string;
  status: OrchestrationPlanStatus;
  affectedFiles: string[];
  subtasks: string[];
  toolPermissions: ToolPermissionRequest[];
  budget: EstimatedBudget;
  estimatedBudget?: EstimatedBudget;
  qualityGates: string[];
  userConfirmed: boolean;
  confirmedBy?: string;
  confirmedAt?: string;
  userDecisionNote?: string;
  createdAt: string;
}

export interface GeneratePlanCommand {
  projectId: string;
  workspaceId: string;
  templateType: string;
  taskGoal: string;
  requirement?: string;
}

export interface ConfirmPlanCommand {
  confirmed: boolean;
  authorizedTools?: string[];
  allowedHighImpactTools?: string[];
  comments?: string;
  decisionNote?: string;
}

export type AgentToolCategory =
  | "FILE_WRITE"
  | "COMMAND_EXEC"
  | "GIT_OPERATION"
  | "DEPLOYMENT"
  | "NETWORK_REQUEST"
  | "FILE_READ"
  | "CODE_ANALYSIS";

export interface AgentToolDefinition {
  name: string;
  category: AgentToolCategory;
  description: string;
  highImpact: boolean;
  parametersSchema: string;
  maxOutputSizeBytes: number;
}

export type EvaluationTaskCategory =
  | "NEW_ENDPOINT"
  | "BUG_FIX"
  | "REFACTOR"
  | "TEST_COMPLETION"
  | "PROMPT_INJECTION_DEFENSE";

export interface EvaluationTaskResult {
  taskId: string;
  category: EvaluationTaskCategory;
  name: string;
  passed: boolean;
  score: number;
  toolMisuse: boolean;
  diffAcceptable: boolean;
  errorDetail?: string;
  outputSummary?: string;
}

export interface EvaluationReportView {
  reportId: string;
  modelIdentifier: string;
  evaluatedAt: string;
  passRate: number;
  toolMisuseRate: number;
  diffAcceptanceRate: number;
  taskResults: EvaluationTaskResult[];
}

// ============================================================================
// Phase 4: Run Operations, Artifact Lineage & Structured JGit Diff Types
// ============================================================================

export interface StepLineageNode {
  stepRunId: string;
  stepId: string;
  status: string;
  artifactId?: string;
  commitHash?: string;
  tagName?: string;
  changedFiles?: string[];
  fileChecksums?: Record<string, string>;
  reviewStatus?: string;
  approvalId?: string;
  approvalStatus?: string;
  qualityGateReport?: QualityGateReport;
  agentRole?: string;
  reworkFeedback?: string;
}

export interface ArtifactLineageView {
  runId: string;
  projectId: string;
  workspaceId?: string;
  baselineCommitHash?: string;
  currentCommitHash?: string;
  stepNodes: StepLineageNode[];
  artifacts: ArtifactView[];
  totalArtifacts: number;
  totalFilesChanged: number;
  isReverted: boolean;
}

export interface PendingDecisionItem {
  decisionType: string;
  referenceId: string;
  stepRunId?: string;
  nodeName: string;
  description: string;
  assignedRole: string;
  requestedAt?: string;
}

export interface ToolSideEffectView {
  toolName: string;
  targetFiles?: string[];
  command?: string;
  status?: string;
  checksums?: Record<string, string>;
}

export interface HierarchicalMemorySummary {
  projectId: string;
  runId: string;
  totalTiers: number;
  totalSteps: number;
  compressedSteps: number;
  estimatedTokens: number;
  budgetExceeded: boolean;
  aociCognitionStatus: string;
  activeContextSources?: string[];
}

export interface LeaseStatusView {
  leaseOwner?: string;
  leaseUntil?: string;
  heartbeatAt?: string;
  isExpired: boolean;
}

export interface RunOperationsView {
  workflowRun: WorkflowRunView;
  stepRuns: StepRunView[];
  unifiedStatus: string;
  nextAction: string;
  requiresHumanIntervention: boolean;
  pendingDecisions: PendingDecisionItem[];
  toolSideEffects: ToolSideEffectView[];
  memorySummary?: HierarchicalMemorySummary;
  artifactLineage?: ArtifactLineageView;
  recentEvents?: RunEventView[];
  leaseStatus?: LeaseStatusView;
  totalRetries?: number;
  failureCategory?: string;
  failureReason?: string;
}
