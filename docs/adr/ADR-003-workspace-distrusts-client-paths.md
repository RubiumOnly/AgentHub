# ADR-003: Workspace 不信任客户端绝对路径与受控路径沙箱解析

* **状态 (Status)**: 已采纳 (Accepted)
* **制定者 (Deciders)**: AgentHub 核心架构组、安全审计小组
* **日期 (Date)**: 2026-10-07
* **技术背景 (Technical Story)**: `docs/AGENTHUB_LONG_TERM_PLAN.md` 阶段 0 安全止血与 `ORIGINAL_REQUEST.md` R2 受控工作区路径治理

---

## 一、 背景与问题陈述 (Context and Problem Statement)

在 AgentHub 平台中，工作区（Workspace）是多个智能体协同读写代码、执行构建、比对 Git 差异与管理工程资产的核心物理场所。

### 历史缺陷与重大安全风险
审查阶段 0 原型代码时发现，文件读写与审计接口存在灾难性的安全设计漏洞：
1. **直接暴露物理绝对路径**：
   - `WorkflowAndDiffController` 的接口（`/api/workspace/files`, `/api/workspace/diff`, `/api/workspace/file/content`, `/api/workspace/file/save`）直接允许前端客户端通过 Query 或 JSON Body 传入任意 `path` 或 `filePath`；
   - 后端控制器直接执行 `new File(path)`，完全未施加合法性与边界校验。
2. **严重的目录穿越与任意文件读写漏洞 (CWE-22)**：
   - 攻击者可构造 `../../../../etc/shadow` 或 `..\..\..\Windows\System32\drivers\etc\hosts` 等恶意入参，突破工作区边界，读取或覆盖宿主机系统的关键文件。
3. **.git 核心元数据被恶意篡改风险**：
   - 接口未限制对 `.git/` 隐藏目录的读写，客户端可直接保存 `.git/config` 或 `.git/hooks/pre-commit`，甚至导致宿主机任意命令执行或 Git 索引损坏。
4. **开发机环境硬编码**：
   - 前端 `page.tsx` 中写死了 Windows 专属绝对路径 `d:/work/agenthub/data/workspaces/default`，系统脱离特定开发机后无法在 Linux 服务器、CI/CD 流水线或容器中正常启动。

必须从架构层面确立对工作区路径的安全信任模型与集中解析机制。

---

## 二、 决策动因 (Decision Drivers)

1. **零信任安全基线 (Zero Trust Input)**：外部客户端传入的一切路径参数均被视为不可信的恶意输入，服务端绝不盲目拼接或直接用于物理 I/O。
2. **Git 版本库完整性保全**：工作区受 Eclipse JGit 版本控制，必须从根源上阻止对工作区内部 `.git` 元数据目录的非法读写。
3. **环境无关性与跨平台部署**：必须彻底解耦操作系统文件系统路径差异，支持 Windows、Linux 及 Docker 容器环境平滑迁移。
4. **多租户与项目隔离基础**：为阶段 1~2 即将引入的多项目、多租户提供清晰且受控的物理/逻辑工作区边界隔离。

---

## 三、 备选方案 (Considered Options)

1. **方案 A：保持直接路径入参，仅在 Controller 中做字符串白名单过滤**
   - 客户端继续传入路径，在 Controller 层调用 `path.contains("..")` 过滤。
   - *缺陷*：极易被 URL 编码（`%2e%2e%2f`）、双重编码、Windows 反斜杠（`..\`）、空字节注入（`\0`）及软链接（Symlink）绕过，且无法解决环境硬编码问题。
2. **方案 B：彻底推迟至阶段 7 容器沙箱 (Container Sandbox Only)**
   - 依赖 Docker 容器挂载卷做隔离，应用层不做路径校验。
   - *缺陷*：远水解不了近渴；阶段 0~6 期间宿主机依然暴露在任意文件读写的重大威胁下，且本地轻量单机运行成本过高。
3. **方案 C：架构层引入受控 `WorkspaceResolver` 集中沙箱解析 (选定)**
   - 确立 **“受控工作区坚决不信任客户端传入的任何绝对路径”** 原则；
   - 接口全面收敛：客户端仅允许传入受控的项目标识 (`projectId` / `workspaceId`) 与工作区内的相对路径 (`relativePath`)；
   - 服务端通过集成的 `WorkspaceResolver` 机制，强制执行 5 重纵深防御校验。

---

## 四、 决策结论 (Decision Outcome)

**采纳方案 C：架构层引入受控 `WorkspaceResolver` 集中沙箱解析机制**。

### 核心实施规范

1. **接口入参全面收敛与改造**：
   - 废除任何接收客户端绝对路径的接口参数设计；
   - 所有工作区文件接口统一重构为接收两类参数：
     * `workspaceId` (或 `projectId`)：合法的受控工作区租户/工程标识；
     * `relativePath`：相对于该工作区根目录的相对路径（例如 `src/main/App.java`）。
2. **五重纵深路径安全防御体系 (The 5-Level Defense)**：
   * **第 1 层：非空与合法性校验**：拒绝 `null`、空字符串、全空格字符及包含空字节 (`\0`)、冒号 (`:`)、Windows 特殊流 (`::$DATA`) 等非法探测字符。
   * **第 2 层：绝对路径拒止**：若相对路径参数以斜杠开头（如 `/foo`、`\foo`）或带有盘符标识（如 `C:`、`D:`），直接抛出 `BusinessException(SECURITY_VIOLATION)` 拒绝执行。
   * **第 3 层：路径规整化消除 (Normalization)**：使用 Java NIO `Path.normalize()` 严格消除所有的 `.` 与 `..` 跳转符，并校验规整化后的相对路径绝对不能以 `..` 开头。
   * **第 4 层：根目录边界包含校验 (Containment Check)**：
     - 将相对路径挂载至服务端配置的受控工作区根目录 (`baseDir.resolve(workspaceId).resolve(relativePath)`)；
     - 调用 `toRealPath()` 或规范化绝对路径，严格校验解析出的目标路径必须以工作区根目录为强制前缀（`targetPath.startsWith(workspaceRoot)`），杜绝任何 Symlink 软链接逃逸。
   * **第 5 层：.git 核心元数据防篡改隔离**：
     - 检查目标路径拆分后的任意组件名，若包含 `.git`（不区分大小写，覆盖 `.git`、`.git/config`、`.git/hooks` 等），坚决阻断任何写操作和文件读取。
3. **统一配置化管理**：
   - 根目录由后端统一配置管理：`agenthub.workspace.base-dir: ${AGENTHUB_WORKSPACE_BASE_DIR:./data/workspaces}`；
   - 本地开发走 `./data/workspaces`，单元测试走临时目录 `./target/test-workspaces`，生产环境通过环境变量动态映射至安全存储卷。
4. **平滑过渡与旧接口收敛 (`resolveLegacyPath`)**：
   - 对于历史兼容接口，即使接收到客户端的旧入参，也必须通过 `WorkspaceResolver.resolveLegacyPath` 强行约束在配置的基础根目录内，彻底剥离外部系统目录的物理访问权限。

### 正面收益 (Positive Consequences)

* **100% 根除路径穿越与任意文件越权漏洞**：黑客与恶意请求无论如何构造相对跳转或伪造绝对路径，均被拦截在受控沙箱根目录内。
* **Git 仓库与审计链条绝对安全**：杜绝了外部篡改 Git 配置、伪造 Commit 链或注入恶意 Hook 的安全隐患。
* **彻底消除开发机路径硬编码**：系统具备了在 Windows、Linux 服务器及标准 Docker 镜像中即插即用的跨平台运行能力。

### 负面代价与应对 (Negative Consequences & Mitigation)

* **前后端契约变动**：前端需要调整调用参数，从传递本地拼装路径改为传递纯相对路径。
  * *应对策略*：在阶段 0 中同步完成 `agenthub-frontend` 相关组件参数调整与联调验证。

---

## 五、 各备选方案优缺点对比 (Pros and Cons of the Options)

### 方案 A：Controller 层简单白名单过滤
* 优势：改动极小。
* 劣势：防守逻辑碎片化，极易遗漏；无法应对复杂的跨平台编码绕过和软链接逃逸；治标不治本。

### 方案 B：纯容器沙箱
* 优势：操作系统级强隔离。
* 劣势：开发联调极重，阶段 0 无法支撑秒级单测和轻量本地运行。

### 方案 C：受控 WorkspaceResolver 集中沙箱 (选定)
* 优势：从应用层架构彻底收敛信任边界，防御严密，跨平台自适应，为多租户打下坚实基石。
* 劣势：需重构部分既有控制器接口入参。

---

## 六、 参考链接 (Links & References)

* `docs/AGENTHUB_LONG_TERM_PLAN.md` - 阶段 0：止血、冻结范围和基线
* `ORIGINAL_REQUEST.md` - R2：受控工作区路径与安全穿透防御
* OWASP Top 10: Path Traversal (https://owasp.org/www-community/attacks/Path_Traversal)
* CWE-22: Improper Limitation of a Pathname to a Restricted Directory ('Path Traversal')
