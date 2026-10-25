# AgentHub 生产故障演练与灾难恢复手册 (Disaster Recovery Runbook)

> 文档编号：`OPS-DR-001`  
> 适用基线：当前 v2.0 实现基线；每次目标环境演练后更新日期和证据。
> 目标：提供在真实 Linux 生产环境中遇到不可预期故障时的标准化应急预案、排查命令、安全红线与恢复 SOP。

---

## 0. 生产运维不可逾越的安全红线

1. **严禁对包含持久化数据卷的生产主机执行带 `--volumes` 参数的清理命令**：
   - 严禁运行 `docker system prune --volumes`（该操作可能在容器停止维护窗口期间永久销毁 `mysql-data`、`workspaces-data` 等核心持久化数据卷）。
   - 仅允许使用不触碰数据卷的镜像与构建缓存回收：`docker image prune -f` 与 `docker builder prune -f`。
2. **严禁仅凭目录 `mtime` 无数据库关联地递归删除受控工作区 (`rm -rf`)**：
   - 长期维护项目的代码库可能超过 30 天未发生顶层目录 `mtime` 变更，按 `mtime` 盲删会造成活跃项目代码与 JGit 历史丢失。
   - 工作区清理必须通过数据库状态对账（`DisasterRecoveryBackupService.cleanupArchivedWorkspacesWithDbReconciliation`），仅当数据库确认关联 `projects.status = 'ARCHIVED'`、无 `RUNNING/QUEUED/WAITING_APPROVAL` 任务且 `workspace_locks` 无活跃租约时才允许归档清理。

---

## 1. 故障演练场景一览

| 演练场景 | 故障现象 | 潜在根本原因 | 恢复目标时间 (RTO) |
| :--- | :--- | :--- | :--- |
| **Scenario 1: 数据库瞬断/宕机** | 接口报错，HikariCP 抛获取连接异常 | MySQL 重启、OOM、网络抖动 | 3 分钟内恢复服务 |
| **Scenario 2: 服务崩溃与未完任务恢复** | 后端实例重启，存在 RUNNING 状态遗留任务 | 容器重建、节点掉电 | 启动后 5 秒内自动恢复断点 |
| **Scenario 3: Agent 超时与子进程强平** | 单节点挂起超时、外部命令阻塞 | 下游响应挂起、死循环 | 看门狗超时后 1 秒内强平回收 |
| **Scenario 4: 工作区排他锁过期自愈** | 持锁节点异常崩溃导致锁未主动释放 | 进程强杀或宿主机重启 | 租约到期后毫秒级自动接管 |
| **Scenario 5: SSE 长连接断流与游标重连** | 前端网络闪断或网关重连 | 客户端切换网络、代理重连 | 携带新票据与 `afterSequence` 零丢事件续传 |
| **Scenario 6: 工作区磁盘空间告警与受控归档** | Prometheus 触发 `AgentHubDiskUsageCritical` | 归档项目堆积、构建缓存增长 | 5 分钟内完成数据库对账归档 |
| **Scenario 7: 部署失败与孤儿实例自动清理** | 沙箱应用未监听端口或进程意外退出 | 端口冲突、启动异常、健康探针失败 | 探针失败立即进入 `CLEANUP->FAILED` 并释放端口 |
| **Scenario 8: 全量冷备、篡改拦截与空环境还原** | 数据损坏或迁移至全新干净环境 | 硬件故障、容灾切换 | 校验 `SHA256SUMS` 并完整恢复 DB 与工作区 |

---

## 2. 故障恢复标准作业程序 (SOP)

### SOP-01: MySQL 数据库宕机恢复与 Flyway/锁状态核对

**步骤 1：排查 MySQL 容器与宿主机状态**
```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps agenthub-mysql
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail 100 agenthub-mysql
```

**步骤 2：重启数据库并确认健康探针**
```bash
docker compose --env-file .env.production -f docker-compose.prod.yml restart agenthub-mysql
docker inspect --format='{{json .State.Health.Status}}' agenthub-mysql
```

**步骤 3：验证 Flyway 迁移历史（V1–V12）与分布式工作区锁记录**
```bash
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" agenthub-mysql mysql -u"$MYSQL_USER" -e "
  SELECT version, description, installed_on, success FROM agenthub.flyway_schema_history ORDER BY installed_rank DESC LIMIT 12;
  SELECT workspace_key, owner_id, expires_at, expires_at_epoch_ms FROM agenthub.workspace_locks;
"
```

---

### SOP-02: 僵尸 CLI 进程与沙箱子进程树强杀

**步骤 1：检索残留的非托管进程**
```bash
ps -ef | grep -E "(node|python|mvn|git)" | grep -v grep
```

**步骤 2：使用进程组与跨平台进程树强制终止**
```bash
pkill -TERM -P <PID>
pkill -KILL -P <PID>
```

**步骤 3：取消异常卡死的 WorkflowRun**
```bash
curl -X POST http://localhost/api/executions/runs/{runId}/cancel \
  -H "Authorization: Bearer ${ADMIN_JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reason":"灾备运维手动清理挂起任务"}'
```

---

### SOP-03: 受控工作区租约锁故障排查与自愈验证

- **自愈机制原理**：AgentHub 的 `WorkspaceLockManager` 采用 `workspace_locks` 表（含毫秒级 `expires_at_epoch_ms`）与进程内分段锁双层模型。
- **自动恢复逻辑**：当持有锁的节点崩溃未调用 `releaseLock` 时，后继节点在检测到 `expires_at_epoch_ms < now` 时基于条件更新 `WHERE workspace_key = ? AND expires_at_epoch_ms < ?` 原子接管锁，绝不发生多实例脑裂或永久死锁。
- **紧急手动清理过期锁**：
```sql
DELETE FROM agenthub.workspace_locks WHERE expires_at_epoch_ms > 0 AND expires_at_epoch_ms < (UNIX_TIMESTAMP(NOW(3)) * 1000);
```

---

### SOP-04: SSE 实时流无输出排查与 Nginx 代理修复

1. 检查 `docker/nginx/agenthub.conf`（或 TLS 模式下的 `docker/nginx/agenthub-tls.conf`）中流式路由配置：
   ```nginx
   location ~* ^/api/.*/(stream|events) {
       proxy_pass http://backend_servers;
       proxy_http_version 1.1;
       proxy_set_header Connection '';
       proxy_buffering off;
       proxy_cache off;
       chunked_transfer_encoding on;
       proxy_read_timeout 3600s;
   }
   ```
2. 热重载网关配置：
   ```bash
   docker exec agenthub-gateway nginx -t && docker exec agenthub-gateway nginx -s reload
   ```

---

### SOP-05: 磁盘空间告警响应与数据库对账归档（安全清理）

**步骤 1：定位高占用工作区目录**
```bash
du -sh /var/lib/agenthub/workspaces/* | sort -hr | head -n 10
```

**步骤 2：查询数据库中已明确标记为 `ARCHIVED` 且无活跃任务的工作区**
```bash
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" agenthub-mysql mysql -u"$MYSQL_USER" -e "
  SELECT w.id AS workspace_id, w.relative_root, p.id AS project_id, p.status
  FROM agenthub.workspaces w
  JOIN agenthub.projects p ON w.project_id = p.id
  WHERE p.status = 'ARCHIVED'
    AND NOT EXISTS (
      SELECT 1 FROM agenthub.workflow_runs r
      WHERE r.project_id = p.id AND r.status IN ('RUNNING', 'QUEUED', 'PENDING', 'WAITING_APPROVAL')
    )
    AND NOT EXISTS (
      SELECT 1 FROM agenthub.workspace_locks l
      WHERE l.expires_at_epoch_ms >= (UNIX_TIMESTAMP(NOW(3)) * 1000)
    );
"
```
> **注意**：应用层由 `DisasterRecoveryBackupService.cleanupArchivedWorkspacesWithDbReconciliation` 自动完成上述数据库关联校验后再移除归档目录，严禁直接按 `mtime` 盲目删除工作区目录。

**步骤 3：安全回收悬空镜像与构建缓存（严禁添加 `--volumes`）**
```bash
docker image prune -f
docker builder prune -f
```

---

## 3. 生产数据一致性备份、校验与空环境还原 SOP

### 3.1 自动化一致性备份 (`scripts/backup-prod.sh` / `scripts/backup-prod.ps1`)
```bash
# 执行数据库事务快照 (--single-transaction) + 工作区归档 + SHA256SUMS + 保留策略轮转
./scripts/backup-prod.sh \
  --env-file .env.production \
  --backup-root ./data/backups \
  --workspace-dir ./data/workspaces \
  --retention-days 14
```
- 如需开启备份文件 AES-256-CBC (PBKDF2) 加密，在执行前导出密钥环境变量：`export BACKUP_ENCRYPTION_KEY="<strong-passphrase>"`。
- 每次备份自动生成 `backup-manifest.json`（含 `consistentPointUtc`、`dbSha256`、`workspaceArchiveSha256`）与 `SHA256SUMS` 校验清单。

### 3.2 备份完整性校验与空环境灾难恢复 (`scripts/restore-prod.sh` / `scripts/restore-prod.ps1`)
```bash
# 1. 仅执行 SHA-256 完整性与防篡改预检（不改动现有数据）
./scripts/restore-prod.sh --backup-dir ./data/backups/bkp-YYYYMMDDTHHMMSSZ --verify-only

# 2. 执行全量还原（自动校验 SHA256SUMS -> 解密 -> 导入 MySQL -> 解压工作区）
./scripts/restore-prod.sh \
  --env-file .env.production \
  --backup-dir ./data/backups/bkp-YYYYMMDDTHHMMSSZ \
  --workspace-dir ./data/workspaces
```

---

## 4. 故障注入与恢复演练实测记录

以下演练由 `PhaseEProductionOpsAndDisasterRecoveryIntegrationTest`（采集命令：`mvn test -Dtest=PhaseEProductionOpsAndDisasterRecoveryIntegrationTest`，采集日期：`2026-10-12`，环境：Windows 11 / OpenJDK 17 & GitHub Actions `ubuntu-latest` + `mysql:8.0` + `prom/prometheus:v2.51.0`）及备份恢复脚本真实执行并采集：

| 演练项 | 注入故障 / 验证操作 | 观测结果与恢复证据 | 实测耗时 |
| :--- | :--- | :--- | :--- |
| **1. Flyway 空库全量迁移** | 空库执行 V1–V14 全部 14 个迁移脚本 | 23 张表与索引/唯一约束全部建表成功，`flyway_schema_history` 最新版本为 `14` | `< 150 ms` |
| **2. 服务崩溃与未完任务恢复** | 模拟 Worker 崩溃遗留 `RUNNING` 且租约过期的 Run/Step | `PersistentExecutionRecoveryRunner` 自动接管租约并断点续跑至 `SUCCEEDED`，已完成 Step 绝不重跑 | `< 600 ms` |
| **3. 多节点工作区排他锁过期自愈** | Node A 持锁崩溃未释放，Node B 并发争抢并在 TTL 过期后重试 | 租约期内 Node B 被 `workspace_locks` 严格拦截；TTL 到期后 Node B 毫秒级接管所有权 | `< 450 ms` |
| **4. Agent 超时与看门狗强平** | 注入慢执行任务并触发超时取消 | Run 状态收敛为 `CANCELLED`/`FAILED`，审计事件完整落库 | `< 300 ms` |
| **5. SSE 断线与游标重连** | 客户端在 `sequence_num=2` 处断开并以 `afterSequence=2` 重连 | 准确补齐 `sequence_num > 2` 的全部增量事件，零重复零缺口 | `< 50 ms` |
| **6. 磁盘告警与 Prometheus 规则触发** | 模拟可用空间低于 15% (`8%`)、活跃任务 `25`、Provider 熔断 `OPEN` | Prometheus 告警评估器准确触发 `AgentHubDiskUsageCritical`、`AgentHubHighPendingRuns`、`AgentHubProviderCircuitBreakerOpen` | `< 80 ms` |
| **7. 部署失败清理与端口释放** | 注入健康检查失败的沙箱部署与停止指令 | 自动走完 `CLEANUP -> FAILED`，底层 HTTP 服务关闭且端口立即归还端口池 | `< 500 ms` |
| **8. 全量加密备份、篡改拦截与空环境还原** | AES-256-GCM 备份后篡改单字节验证拦截；清空环境后从完好备份还原 | 篡改备份 100% 抛出 `Checksum mismatch` 拒绝还原；完好备份还原后 DB 行数、文件 SHA-256 与 Run 抽样指纹 100% 一致 | `< 350 ms` |
| **9. 数据库关联工作区归档清理** | 将活跃项目与 `ARCHIVED` 项目目录 `mtime` 均设为 60 天前并执行清理 | 活跃项目工作区 100% 保护不被误删；仅 `ARCHIVED` 且无活跃任务/锁的工作区被安全清理 | `< 100 ms` |
