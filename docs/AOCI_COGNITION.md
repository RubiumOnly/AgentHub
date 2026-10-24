# AOCI 认知治理运维文档

本文是 AgentHub 仓库 AOCI（Agent 仓库认知层）资产、版本、接入方式与恢复流程的唯一权威运维文档。业务代码、能力边界和接口契约不在此重复。

## 1. 资产清单与 Git 边界

| 资产 | 提交状态 | 说明 |
| --- | --- | --- |
| `aoci.txt` | 提交 | Root 认知清单（cognition-volumes/v1），声明 meta 与 code 两个 Volume |
| `aoci.meta.txt` | 提交 | Meta Volume：对象协议、FRAS 纪律、标签字典与 S 配额 |
| `aoci.code.txt` | 提交 | Code Volume：受管文件的 F/R/A/S 认知条目 |
| `.aoci/config.json` | 提交 | 治理配置：排除规则、Managed Scope、预算、自动化模式 |
| `.aoci/baseline.json` | 提交 | 受管范围全量文件指纹基线 |
| `.aoci/.gitignore` | 提交 | `.aoci` 目录资产白名单（默认拒绝未知文件入库） |
| `.aoci/ledger.jsonl` | 本机运行时产物 | 写入与审计流水，不入库 |
| `.aoci/verify_history/` | 本机运行时产物 | verify 快照，不入库 |
| `.mcp.json` | 提交 | MCP 宿主接入配置（command 指向本机 CLI 绝对路径） |
| `.codex/config.toml` | 不提交（机器绑定） | Codex 宿主 MCP 接入配置 |

## 2. 官方 CLI 版本与来源

- 版本：`aoci 0.1.0-rc21`（commit `cfa8e93ca9fe546a375f48b41b3d4766ed98b013`，构建于 2026-10-14）。
- 本机安装路径：`C:\tools\aoci\0.1.0-rc21\aoci.exe`（不在 PATH 上；CLI 路径以项目 `.mcp.json` 的 `command` 为准）。
- 安装与升级必须走官方发布物或可复现源码构建；替换二进制后必须重新执行 `verify` 并记录到本文第 4 节。

## 3. Managed Scope 与扫描边界

- Profile：`production`；`observe_change_policy=review_required`。
- 排除：`target/`、`node_modules/`、`.next/`、`dist/`、`build/`、`vendor/` 等构建产物目录，`*.backup.*`、`*.bak`、`.coverage` 等机器文件。
- 内置 observe 规则针对 vendored 静态资源（`*.min.js`、fonts 等）；本仓库无该类路径，保留规则不生效。
- `reference-shrimp-crab/` 为被 Git 忽略的参考仓库，不在受管范围内，不得据此复制功能。

## 4. 当前治理状态（2026-10-24 实测，Database Cognition 完成后）

- Code Cognition：586 条条目全量覆盖受管 Code 对象（400 后端生产 Java、62 测试 Java、前端、docs、CI、Docker、配置），S 覆盖 100%，E 规模档位不符 0/586。
- Database Cognition：数据源 `agenthub-mysql-evidence`（MySQL，元数据只读，`business_data_read=false`）；Evidence Baseline 23 张表（22 业务表 + flyway_schema_history），`source_snapshot_sha256=61ca4caf...78bb0d`；23 条表级 Database Entry 已全部入库（aoci.database.txt）。
- 治理判定：`aoci check` 五净（findings=0、governance_aligned=true、structure_valid=true）；`aoci verify` 结构有效。
- 模型认知：Whole-Index 全量交付（6 块、约 40430 tokens）已完成，Attestation 通过（Challenge 10/10，cognition_verified）。Database Volume 在 Attestation 之后建立，认知收据将在下一次 Overview 刷新时升级为 governed（常规刷新，非阻塞项）。
- 治理对齐历史：observe_change_policy 已由仓库所有者亲自批准（preview→approve→apply，事务 00a8b24b）切换为 informational，解除观察文件指纹死锁。

## 5. MCP 宿主接入

- 项目级 `.mcp.json` 已配置 `aoci` MCP stdio server（`aoci --repo <仓库根> mcp`），支持的宿主在会话中提供 `aoci_rules`、`aoci_overview`、`aoci_maintain`、`aoci_update_entry` 等工具。
- MCP 工具未加载的会话可按 `.mcp.json` 的 command 以 stdio JSON-RPC 直连官方 server 驱动同一次调用（initialize → tools/call），合同与分块语义与 MCP 工具完全一致。
- Database Cognition 数据源：source_id `agenthub-mysql-evidence`，凭证经环境变量 `AGENTHUB_AOCI_DB_URL`（Go DSN 形式 `root:<密码>@tcp(<主机>:<端口>)/agenthub`）注入，不出现在任何入库文件。本地证据容器为 docker MySQL 8.4（与 CI 的 8.0 存在小版本差异，已如实记录）；执行 `aoci database snapshot/verify` 前需启动该容器。
- 恢复顺序：`aoci doctor` 诊断环境 → `aoci status` 速览 → `aoci verify` 结构校验 → `aoci check` 漂移与治理判定。基线损坏时用 `aoci scan` 重建指纹，再走官方 Baseline 治理流程；不得手改 `baseline.json`、`aoci.txt` 或 `aoci.database.txt` 正文。

## 6. 使用铁律

1. 严禁伪造 `aoci.txt`、`.aoci`、Receipt、Ledger 或 aligned 状态；`verify/check` 未通过时不得宣称对齐。
2. AOCI 不可用或未对齐时，如实记录阻塞；继续的工作只能依赖源码、测试与其他真实证据。
3. 业务代码修改后，受影响 Entry 在对象达到最终稳定状态时通过一次 `aoci_maintain`（或 CLI 等价管线）维护。
