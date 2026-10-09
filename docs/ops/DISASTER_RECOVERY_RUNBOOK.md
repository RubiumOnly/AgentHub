# AgentHub 生产故障演练与灾难恢复手册 (Disaster Recovery Runbook)

> 文档编号：`OPS-DR-001`  
> 归属版本：`v1.9.0 (Enterprise Release)`  
> 目标：提供在真实 Linux 生产环境中遇到不可预期故障时的标准化应急预案、排查命令与恢复 SOP。

---

## 1. 故障演练场景一览

| 演练场景 | 故障现象 | 潜在根本原因 | 恢复目标时间 (RTO) |
| :--- | :--- | :--- | :--- |
| **Scenario 1: 数据库宕机** | 接口大面积 500，HikariCP 抛获取连接超时 | MySQL 崩溃、OOM 杀进程、网络隔离 | 3 分钟内恢复服务 |
| **Scenario 2: Agent 子进程泄漏** | 系统句柄耗尽、CPU 100%、文件被锁占用 | 外部 CLI/命令挂起、未响应 SIGTERM | 2 分钟内清理完毕 |
| **Scenario 3: 工作区锁永久占有** | 任何写操作均提示工作区被占用 | 服务极端异常宕机导致锁未主动释放 | 自动在 60s 租约期后自愈 |
| **Scenario 4: SSE 长连接断崖式断流** | 前端界面控制台出现断线或无实时输出 | Nginx 开启了 proxy_buffering 或网关超时 | 1 分钟内定位网关参数 |
| **Scenario 5: 工作区磁盘爆满** | Git 提交失败、写入提示 `No space left` | 构建产物过度堆积、未开启日志轮转 | 5 分钟内释放空间 |

---

## 2. 故障恢复标准作业程序 (SOP)

### SOP-01: MySQL 数据库宕机恢复与数据一致性核对

**步骤 1：排查 MySQL 容器与宿主机状态**
```bash
# 查看容器运行状态与最后报错日志
docker compose -f docker-compose.prod.yml ps agenthub-mysql
docker compose -f docker-compose.prod.yml logs --tail 100 agenthub-mysql
```

**步骤 2：重启数据库并确认健康探针**
```bash
# 重新启动数据库服务
docker compose -f docker-compose.prod.yml restart agenthub-mysql

# 验证健康检查返回 healthy
docker inspect --format='{{json .State.Health.Status}}' agenthub-mysql
```

**步骤 3：验证 Flyway 迁移状态与锁记录**
```bash
# 进入容器检查数据库表完整性与当前锁状态
docker exec -it agenthub-mysql mysql -uagenthub_app -p -e "
  SELECT version, description, installed_on, success FROM agenthub.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
  SELECT workspace_key, owner_id, expires_at FROM agenthub.workspace_locks;
"
```

---

### SOP-02: 僵尸 CLI 进程与沙箱子进程树强杀

**步骤 1：检索残留的非托管进程**
```bash
# 查找处于无父进程孤儿状态的 node/python/java/cli 进程
ps -ef | grep -E "(node|python|mvn|git)" | grep -v grep
```

**步骤 2：使用进程组与跨平台进程树强制终止**
```bash
# 终止特定进程及其派生的所有子进程树 (Linux)
pkill -TERM -P <PID>
# 若未响应则强制 SIGKILL
pkill -KILL -P <PID>
```

**步骤 3：重置处于卡死 RUNNING 状态的任务**
```bash
# 针对因宿主机掉电未能正常落入终态的 Run，执行运维降级置位
curl -X POST http://localhost:8080/api/runs/{runId}/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"灾备运维手动清理挂起任务"}'
```

---

### SOP-03: 受控工作区租约锁故障排查与自愈验证

- **自愈机制原理**：AgentHub 的 `WorkspaceLockManager` 采用了分布式租约锁模型。每个锁具备 `leaseTtlMs`（默认 60 秒）和 `expiresAt` 时间戳。
- **自动恢复逻辑**：当持有锁的进程崩溃未调用 `releaseLock` 时，后继请求会在检测到 `expiresAt < now` 时打印警告日志并自动接管该锁（Auto-Recovery），不会导致永久死锁。
- **紧急手动释放（若需要）**：
```sql
-- 仅在极端紧急情况下清除过期锁
DELETE FROM agenthub.workspace_locks WHERE expires_at < NOW();
```

---

### SOP-04: SSE 实时流无输出排查与 Nginx 代理修复

**症状**：前端无法收到逐行打印输出，任务完成后一次性吐出所有内容。

**根因**：中间代理服务器（如 Nginx）开启了缓冲 (`proxy_buffering on`)，导致流式 Chunk 被暂存直到缓冲区填满。

**验证与修复检查项**：
1. 检查 `/etc/nginx/nginx.conf` 中对应路由配置：
   ```nginx
   location ~* ^/api/.*/(stream|events) {
       proxy_pass http://backend_servers;
       proxy_http_version 1.1;
       proxy_set_header Connection '';
       proxy_buffering off;          # 关键：必须显式关闭
       proxy_cache off;              # 关键：必须显式关闭
       chunked_transfer_encoding on; # 启用分块传输
       proxy_read_timeout 3600s;     # 必须足够长防止代理主动切断
   }
   ```
2. 热重载 Nginx：
   ```bash
   docker exec agenthub-gateway nginx -t && docker exec agenthub-gateway nginx -s reload
   ```

---

### SOP-05: 磁盘爆满应急清理与数据卷归档

**步骤 1：定位大文件与高占用目录**
```bash
du -sh /var/lib/agenthub/workspaces/* | sort -hr | head -n 10
```

**步骤 2：清理过期运行产生的临时沙箱快照**
```bash
# 保留最近 30 天的工作区快照，清理超过 30 天且对应 Run 已标记归档的目录
find /var/lib/agenthub/workspaces -mindepth 1 -maxdepth 1 -type d -mtime +30 -exec rm -rf {} +
```

**步骤 3：清理 Docker 废弃悬空构建缓存**
```bash
docker system prune -f --volumes
```

---

## 3. 生产数据冷备份与全量还原 SOP

### 3.1 自动化冷备脚本示例 (`scripts/backup.sh`)
```bash
#!/usr/bin/env bash
BACKUP_DIR="/data/backups/agenthub/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

# 1. 导出 MySQL 全量结构与数据
docker exec agenthub-mysql mysqldump -uagenthub_app -p"${MYSQL_PASSWORD}" \
  --single-transaction --quick agenthub > "$BACKUP_DIR/agenthub_db.sql"

# 2. 压缩备份受控工作区 Git 仓库
tar -czf "$BACKUP_DIR/workspaces.tar.gz" -C /var/lib/agenthub/workspaces .

echo "Backup created successfully at: $BACKUP_DIR"
```

### 3.2 全量灾难恢复还原
```bash
# 1. 还原数据库
docker exec -i agenthub-mysql mysql -uagenthub_app -p"${MYSQL_PASSWORD}" agenthub < /data/backups/agenthub/xxx/agenthub_db.sql

# 2. 还原工作区文件
tar -xzf /data/backups/agenthub/xxx/workspaces.tar.gz -C /var/lib/agenthub/workspaces

# 3. 启动并校验健康状态
docker compose -f docker-compose.prod.yml restart
```
