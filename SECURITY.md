# 安全策略与凭证管理规范

AgentHub 涉及多智能体协同、本地命令行进程调用以及第三方大模型 API 通信。请将系统运行在可控的环境中，并严格遵守以下最小权限与凭证隔离策略。

---

## 一、 敏感凭证与密钥隔离 (Credentials Management)

- **严禁代码硬编码**：切勿在任何 Issue、Pull Request、Git 提交、测试用例或前端组件中写入真实的 API Key、云平台 Token 或敏感密码；
- **配置多层隔离机制**：
  - 公共代码库仅保留环境变量占位配置（例如 `${DEEPSEEK_API_KEY:}`）；
  - 本地联调私有配置统一使用 `.env` 或 `application-local.yml`；
  - 该类文件已被 [`.gitignore`](.gitignore) 全局严格排除，绝不会被 Git 跟踪或提交；
- **凭据泄露应急处置**：若任何密钥意外暴露在传输链路、截屏或公共渠道中，请务必立即登录模型提供商控制台予以**注销并重新生成 (Revoke & Regenerate)**。

---

## 二、 进程与物理工作区隔离 (Process & Workspace Security)

- **子进程管道执行看门狗**：
  - 针对本地 CLI（如 Claude Code、Codex）适配器，系统强制设置了执行超时看门狗（Watchdog），杜绝死锁与失控僵尸进程；
  - 进程标准输入输出流经过正则脱敏过滤器，避免外部命令日志泄露运行上下文。
- **物理工作区并发锁防冲突**：
  - 协作生成的文件写入 `data/workspaces/` 目录，通过 `WorkspaceLockManager` 在内存与线程维度实行读写互斥，防止多 Agent 并发写入导致的文件损坏或脏写风险。

---

## 三、 提示词注入防御 (Prompt Injection Guard)

- 多 Agent 协同接收的用户指令与第三方文本输入均属于不可信数据；
- 编排器（Orchestrator）将指令与任务定义严格区分，系统提示词中施加了不可绕过的安全隔离围栏；
- **受控工作区安全解析 (WorkspaceResolver)**：系统在阶段 0 治理中全面落地 `WorkspaceResolver` 机制（详见 [ADR-003](docs/adr/ADR-003-workspace-distrusts-client-paths.md) 与 [《当前真实能力边界与架构现状白皮书》](docs/CURRENT_CAPABILITY_BOUNDARIES.md)），收敛所有文件读写接口，全面拒止客户端传入未校验的绝对路径，严禁任何跳出受控工作区根目录的相对路径跳转（如 `../` 越权攻击）及对 `.git` 目录的非法修改。

---

---

## 四、 身份可信链与多租户资源边界 (Identity & Access Boundary Defense)

- **JWT 密钥生产硬化**：生产环境（`prod` profile）必须通过外部环境变量 `AGENTHUB_AUTH_SECRET` 提供至少 32 字符的高强密钥。应用启动时自动检测弱密码、占位符或示例默认值，一旦命中立即熔断拒绝启动。
- **杜绝生产身份旁路**：生产环境下全面废止 `dev-token-*` 通路与 `X-User-Id` 请求头信任；彻底消除 `user-1` / `system` 硬编码超级用户特权，所有数据操作均严格校验资源归属。
- **SSE 流安全接入 (Stream Ticket & Cookie)**：禁止在 URL 查询参数中传输长期 Bearer Token。前端使用同源 Cookie 或通过 `POST /api/auth/stream-ticket` 换取 60 秒一次性短时票据进行 SSE 连接。
- **服务端派生工作区锁属主**：锁获取接口不再信任客户端传入的 `ownerId`，由服务端依据认证主体自动绑定；锁状态、续租与释放操作均实行多租户隔离校验。
- **持久化 Token 吊销与认证审计**：用户登出与令牌轮换废止记录写入数据库持久化表 `invalidated_tokens`，跨实例与服务重启后持续生效；全量审计认证生命周期事件至 `auth_audit_logs`。
- **防爆破速率限制**：针对注册、登录等高风险认证端点启用滑动窗口速率限制防御。

---

## 五、 漏洞上报 (Reporting Security Vulnerabilities)

如发现任何潜在的安全隐患、越权访问风险或逻辑缺陷，请不要公开在 GitHub Issues 中讨论，欢迎通过 GitHub 仓库主页的 **Security -> Advisories** 渠道发起私密上报，我们将尽快确认并推送安全补丁。
