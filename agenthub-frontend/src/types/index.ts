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
  | "CANCELLED";

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

export interface FileDiffEntry {
  oldPath: string;
  newPath: string;
  changeType: "ADD" | "MODIFY" | "DELETE" | "RENAME" | "COPY";
  diffContent: string;
  linesAdded: number;
  linesDeleted: number;
}

export interface StructuredDiff {
  workspaceId: string;
  baseCommit: string;
  targetCommit: string;
  totalFilesChanged: number;
  totalLinesAdded: number;
  totalLinesDeleted: number;
  entries: FileDiffEntry[];
  clean: boolean;
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
  stepRunId: string;
  artifactType: string;
  path: string;
  checksum: string;
  status: "CREATED" | "ACCEPTED" | "REJECTED" | "REVERTED";
  reviewerId?: string;
  reviewReason?: string;
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

export type DeploymentStatus = "CREATED" | "BUILDING" | "RUNNING" | "STOPPED" | "FAILED";

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
}

export interface CreateDeploymentRequest {
  projectId: string;
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

