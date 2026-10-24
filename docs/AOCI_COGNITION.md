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

## 4. 当前治理状态（2026-10-24 实测）

- `aoci scan` 基线：588 个受管文件（2026-10-17 建立）。
- 认知条目：0 条；`aoci check` 判定 `authoring_required`，584 项 `code_missing` finding。
- `aoci verify`：Volumes v1 候选结构有效（root/meta SHA-256 与 composite identity 已固化在 `.aoci/verify_history/`）。
- 治理对齐：未达成。条目创作必须由宿主模型基于真实证据逐条完成（F/R/A/S），经官方 `update-entry` 或 MCP `aoci_update_entry` 管线写入；禁止依据路径名、模板或规则引擎批量编造。

## 5. MCP 宿主接入

- 项目级 `.mcp.json` 已配置 `aoci` MCP stdio server（`aoci --repo <仓库根> mcp`），支持的宿主在会话中提供 `aoci_rules`、`aoci_overview`、`aoci_maintain`、`aoci_update_entry` 等工具。
- MCP 工具未加载的会话应使用 CLI 只读命令（`status`、`verify`、`check`、`doctor`）获取状态；正式写入走 `update-entry`（需绑定 `aoci_maintain` 语义的源码 SHA-256）。
- 恢复顺序：`aoci doctor` 诊断环境 → `aoci status` 速览 → `aoci verify` 结构校验 → `aoci check` 漂移与治理判定。基线损坏时用 `aoci scan` 重建指纹，再走官方 Baseline 治理流程；不得手改 `baseline.json` 或 `aoci.txt` 正文。

## 6. 使用铁律

1. 严禁伪造 `aoci.txt`、`.aoci`、Receipt、Ledger 或 aligned 状态；`verify/check` 未通过时不得宣称对齐。
2. AOCI 不可用或未对齐时，如实记录阻塞；继续的工作只能依赖源码、测试与其他真实证据。
3. 业务代码修改后，受影响 Entry 在对象达到最终稳定状态时通过一次 `aoci_maintain`（或 CLI 等价管线）维护。
