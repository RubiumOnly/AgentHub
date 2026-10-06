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
- 生成的代码在落盘与沙箱挂载前经过路径遍历检查，严禁任何跳出 `data/workspaces/` 沙箱根目录的相对路径跳转（如 `../` 越权攻击）。

---

## 四、 漏洞上报 (Reporting Security Vulnerabilities)

如发现任何潜在的安全隐患、越权访问风险或逻辑缺陷，请不要公开在 GitHub Issues 中讨论，欢迎通过 GitHub 仓库主页的 **Security -> Advisories** 渠道发起私密上报，我们将尽快确认并推送安全补丁。
