# 贡献指南 (Contributing Guide)

感谢你关注并参与 **AgentHub** 开源项目！我们欢迎社区提交代码缺陷修复、适配新型智能体运行时、优化协同交互体验或完善技术文档。

---

## 一、 开发环境准备

- **Java 环境**：Java 17 LTS（推荐 Eclipse Temurin 或 Oracle OpenJDK）；
- **Maven**：Maven 3.8+；
- **Node.js**：Node.js 18+ 与 npm；
- **Git**：Git 2.30+。

---

## 二、 本地开发与联调流程

### 1. 克隆代码与分支规范
```bash
git clone https://github.com/RubiumOnly/AgentHub.git
cd AgentHub

# 从 main 检出特性分支（分支命名规范：feat/特性名 或 fix/问题修复）
git checkout -b feat/your-feature-name
```

### 2. 本地私有配置（禁止提交密钥）
- 复制根目录模板文件：
  - 后端：在 `agenthub-backend/src/main/resources/` 下创建 `application-local.yml`（已受 `.gitignore` 保护）；
  - 或配置环境变量 `DEEPSEEK_API_KEY=your_key`。

### 3. 代码质量与全量测试验证
在提交任何 Pull Request 之前，必须确保全量单元测试与前端构建均 100% 绿灯：

```bash
# 1. 验证后端 Spring Boot 测试套件
cd agenthub-backend
mvn clean test

# 2. 验证前端 Next.js 编译构建（确保 0 类型与 Lint 错误）
cd ../agenthub-frontend
npm ci
npm run build
```

---

## 三、 提交信息规范 (Conventional Commits)

本项目**严格要求使用中文语义化提交规范**，统一格式如下：

- `feat: 新增功能说明`（例如：`feat: 接入 Claude Code CLI 运行时流式输出适配器`）
- `fix: 缺陷修复说明`（例如：`fix: 解决多 Agent 并发写入文件时的锁竞争死锁问题`）
- `docs: 文档变更说明`（例如：`docs: 完善核心架构设计说明书与API契约`）
- `refactor: 重构优化说明`（例如：`refactor: 优化 IMCollaborationService 事件分发机制`）
- `test: 测试用例补充`（例如：`test: 补充 JGit 工作区行级 Diff 边界测试用例`）

---

## 四、 提交 PR (Pull Request) 规范

1. 确保修改范围明确、职责单一，避免大而全的无意义混杂提交；
2. PR 描述中清晰说明修改背景、解决的核心问题及本地真实验证截图或测试结果；
3. 遵循代码风格，避免引入非必要的外部第三方库。
