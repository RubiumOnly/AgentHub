# AgentHub API 接口与事件流规范

本文档定义 AgentHub 核心后端服务的 RESTful 接口契约、身份鉴权机制与 Server-Sent Events (SSE) 事件流协议。

---

## 一、 统一响应结构与链路追踪

所有 RESTful 接口均返回标准统一响应体 `Result<T>`，并在响应 Header 中附带追踪标识：
- 响应头：`X-Request-ID: <uuid>`, `X-Correlation-ID: <uuid>`

```json
{
  "code": 0,
  "message": "Operation Successful",
  "data": { ... },
  "timestamp": 1791287780319,
  "requestId": "req-9843c9b7-08b5-4df3"
}
```

- `code == 0`：操作成功；
- `code != 0`：业务或系统异常，`message` 包含具体可读的中文异常原因，`requestId` 用于全链路日志排查。

### 核心业务错误码字典 (ErrorCode)
- `1001` UNAUTHORIZED：未提供认证令牌或令牌已失效；
- `1002` FORBIDDEN：无权访问或修改此资源（跨租户/跨项目越权拦截）；
- `1003` INVALID_CREDENTIALS：用户名或密码错误；
- `1004` USER_ALREADY_EXISTS：该邮箱已被注册；
- `2001` PROJECT_NOT_FOUND：目标项目不存在；
- `3001` WORKSPACE_NOT_FOUND：目标工作区不存在；
- `3004` DIRECTORY_TRAVERSAL_FORBIDDEN：工作区路径穿越违规；
- `3005` GIT_DIRECTORY_MODIFICATION_FORBIDDEN：禁止直接读写 `.git` 内部文件；
- `4001` CONVERSATION_NOT_FOUND：协同会话不存在；
- `4003` CONVERSATION_PERMISSION_DENIED：非当前会话参与者；
- `5001` AGENT_NOT_FOUND：智能体定义或实例不存在；
- `6001` RUN_NOT_FOUND：执行实例不存在；
- `9001` INTERNAL_ERROR：系统内部未捕获异常。

---

## 二、 核心 RESTful 端点契约

### 1. 身份认证与用户体系 (Identity & Auth)
所有受保护接口通过请求头传递 `Authorization: Bearer <token>`：
- `POST /api/auth/register`：用户注册
  - Request: `{"username": "alice", "email": "alice@agenthub.com", "password": "Password123!"}`
  - Response: `Result<AuthTokenView>` (`{"token": "...", "tokenType": "Bearer", "expiresIn": 86400, "user": {...}}`)
- `POST /api/auth/login`：用户登录
  - Request: `{"email": "alice@agenthub.com", "password": "Password123!"}`
  - Response: `Result<AuthTokenView>`
- `GET /api/auth/me`：获取当前登录用户的个人信息
  - Response: `Result<UserView>` (`{"id": "...", "username": "...", "email": "...", "status": "ACTIVE"}`)

### 2. 项目与代码工作区 (Project & Workspace)
- `POST /api/projects`：创建研发项目（自动绑定受控工作区）
  - Request: `{"name": "E-Commerce-Core", "description": "核心电商交易中台", "relativeWorkspacePath": "ecommerce-core"}`
  - Response: `Result<ProjectView>` (`{"id": "proj-...", "ownerId": "...", "name": "...", "status": "ACTIVE", "workspaceId": "ws-..."}`)
- `GET /api/projects`：获取当前用户的所有项目列表
  - Response: `Result<List<ProjectView>>`
- `GET /api/projects/{id}`：获取指定项目详情（触发水平越权检查）
  - Response: `Result<ProjectView>`
- `GET /api/projects/{id}/workspaces`：获取项目绑定的受控工作区列表
  - Response: `Result<List<WorkspaceView>>`
- `GET /api/projects/{id}/workspaces/{workspaceId}/files`：安全提取工作区文件目录树
  - Response: `Result<WorkspaceFileNode>`

### 3. 智能体注册表与实例 (Agent Registry & Instances)
- `GET /api/agents`：查询平台已注册的 Agent 能力定义清单与可用状态；
- `GET /api/agents/instances`：查询当前可调度的智能体实例列表与运行时配置。

### 4. 即时通讯协同总线 (IM & Collaboration)
- `GET /api/im/conversations`：获取协同会话列表（返回 `ConversationView`，含参会成员 `participantIds`）；
- `POST /api/im/conversations`：创建会话（入参 `CreateConversationCommand`：title, type, participantIds）；
- `GET /api/im/conversations/{id}/messages`：查询历史消息列表（返回 `MessageView`，具备单调递增 `sequenceNum` 与 `schemaVersion: "v1"`）；
- `POST /api/im/conversations/{id}/messages`：发送消息与触发智能体路由（入参 `SendMessageCommand`）；
- `GET /api/im/conversations/{id}/stream`：**SSE 实时事件流通道**。

### 5. 工作区版本审计与执行 (Workspace & JGit Diff)
- `GET /api/workspace/diff`：基于 Eclipse JGit 计算受控工作区的 Unified Diff 列表（包含文件路径、变更行数统计与完整 diff patch）；
- `GET /api/workspace/files`：获取受控工作区的文件与目录树结构；
- `POST /api/workspace/files/save`：在线编辑并保存受控工作区代码；
- `POST /api/workspace/workflow/execute`：触发 DAG 拓扑工作流引擎按序执行各阶段节点。

### 6. 模版预览与部署清单生成 (Sandbox & Deploy)
- `GET /api/sandbox/preview/{id}`：挂载并实时预览 Agent 协同生成的 Web 静态模版页面；
- `POST /api/sandbox/deploy/{id}`：自动化导出 Nginx Dockerfile 镜像构建配置与部署清单。

### 7. 系统探针 (Health Check)
- `GET /api/system/health`：获取系统状态、Java 运行时版本、操作系统与应用版本。

---

## 三、 SSE 事件类型定义

通过 `GET /api/im/conversations/{id}/stream` 订阅后，服务端推送以下标准事件：

| 事件名称 | 数据载荷格式 | 场景说明 |
| :--- | :--- | :--- |
| `connected` | `{"conversationId": "...", "status": "ready"}` | 客户端握手建立连接时发送 |
| `message` | `MessageView` 完整 JSON 结构（包含 `sequenceNum` 与 `schemaVersion`） | 收到新消息、Orchestrator 交互卡片或阶段完成通知 |
| `message_delta` | `{"sender": "BackendArchitect", "delta": "..."}` | 智能体实时打字机流式 Token 增量 |
| `heartbeat` | `{"timestamp": 179128...}` | 保持长连接活性的周期心跳包 |

