# AgentHub API 接口与事件流规范

本文档定义 AgentHub 核心后端服务的 RESTful 接口契约与 Server-Sent Events (SSE) 事件流协议。

---

## 一、 统一响应结构

所有 RESTful 接口均返回标准统一响应体：

```json
{
  "code": 0,
  "message": "Operation Successful",
  "data": { ... },
  "timestamp": 1791287780319
}
```

- `code == 0`：操作成功；
- `code != 0`：业务或系统异常，`message` 包含具体可读的中文异常原因。

---

## 二、 核心端点概览

### 1. 系统探针 (Health & Capabilities)
- `GET /api/system/health`：获取系统状态、Java 运行时版本、操作系统与应用版本；
- `GET /api/agents`：查询当前已注册可用的 Agent 智能体列表及其版本信息与可用状态。

### 2. 即时通讯协同总线 (IM & Collaboration)
- `GET /api/im/conversations`：获取会话列表；
- `POST /api/im/conversations`：创建协同会话（单聊或群聊），入参包含会话标题、类型与参会 Agent 标识；
- `GET /api/im/conversations/{id}/messages`：查询指定会话的历史消息列表；
- `POST /api/im/conversations/{id}/messages`：发送消息（支持自然语言、指令及 `@Mention` 指令路由）；
- `GET /api/im/conversations/{id}/stream`：**SSE 实时事件流通道**。

### 3. 工作区与版本审计 (Workspace & JGit Diff)
- `GET /api/workspace/diff?path={workspacePath}`：基于 Eclipse JGit 计算并返回当前物理工作区的 Unified Diff 列表（包含文件路径、变更类型、变更行数统计与完整 diff patch 文本）；
- `GET /api/workspace/files?path={workspacePath}`：获取物理工作区的文件与目录树结构；
- `POST /api/workspace/files/save`：在线编辑并保存工作区文件代码；
- `POST /api/workspace/workflow/execute`：触发 DAG 拓扑工作流引擎按序执行各阶段节点。

### 4. 沙箱与容器化部署 (Sandbox & Deploy)
- `GET /api/sandbox/preview/{id}`：挂载并实时预览 Agent 协同生成的 Web 静态页面或前端单页应用；
- `POST /api/sandbox/deploy/{id}`：自动化生成生产级 Dockerfile 与部署清单。

---

## 三、 SSE 事件类型定义

通过 `GET /api/im/conversations/{id}/stream` 订阅后，服务端推送以下标准事件：

| 事件名称 | 数据载荷格式 | 场景说明 |
| :--- | :--- | :--- |
| `connected` | `{"conversationId": "...", "status": "ready"}` | 客户端握手建立连接时发送 |
| `message` | `MessageEntity` 完整 JSON 结构 | 收到新消息、Orchestrator 交互卡片或阶段完成通知 |
| `message_delta` | `{"sender": "BackendArchitect", "delta": "..."}` | 智能体实时打字机流式 Token 增量 |
| `heartbeat` | `{"timestamp": 179128...}` | 保持长连接活性的周期心跳包 |
