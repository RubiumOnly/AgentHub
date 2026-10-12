# AgentHub Prometheus 告警规则与观测策略

> 文档编号：`OPS-ALERT-001`  
> 归属版本：`v2.0.0 (Phase E Hardened)`  
> 生效环境：`Production & Staging`  
> 机器可加载配置文件：`docker/prometheus/prometheus.yml` 与 `docker/prometheus/agenthub-alerts.yml`

---

## 1. 告警架构与安全访问控制

AgentHub 采用分层防御的监控告警体系，并在网关与应用层对观测端点实施最小权限管控：
- **端点鉴权与网络隔离**：
  - `/actuator/health` 保持公开（生产环境下 `show-details: never` 隐藏内部组件拓扑细节），供容器健康探针探测；
  - `/actuator/prometheus`、`/actuator/metrics` 与 `/actuator/info` 由 Nginx 网关（`docker/nginx/agenthub.conf` / `agenthub-tls.conf`）限制仅允许内网抓取，并由后端 `ActuatorSecurityFilter` 强制校验受信内网来源或 `X-Observability-Token` / `Authorization: Bearer <ACTUATOR_PROMETHEUS_TOKEN>` 凭据，阻断公网未授权抓取。
- **可加载配置文件校验**：
  - 告警规则物理落盘于 `docker/prometheus/agenthub-alerts.yml`，由 `docker/prometheus/prometheus.yml` 通过 `rule_files` 加载；
  - CI 流水线通过官方 `prom/prometheus:v2.51.0` 的 `promtool check config` 与后端 `PrometheusAlertEvaluator` 双重校验语法与触发逻辑。

---

## 2. Prometheus Alertmanager 规则定义 (`docker/prometheus/agenthub-alerts.yml`)

```yaml
groups:
  - name: agenthub_core_alerts
    rules:
      # 1. 任务积压与执行死锁告警
      - alert: AgentHubHighPendingRuns
        expr: agenthub_runs_active > 20
        for: 5m
        labels:
          severity: warning
          team: platform-core
        annotations:
          summary: "AgentHub 执行内核任务积压严重"
          description: "当前活跃并发运行的 Run 数量为 {{ $value }}，超过安全阈值 20，持续时间超过 5 分钟，可能存在调度器线程池饥饿或下游模型并发受限。"

      - alert: AgentHubRunFailureSpike
        expr: sum(rate(agenthub_runs_total{status="FAILED"}[5m])) / sum(rate(agenthub_runs_total[5m])) > 0.05
        for: 3m
        labels:
          severity: critical
          team: platform-core
        annotations:
          summary: "AgentHub 工作流失败率激增"
          description: "过去 5 分钟内工作流失败率达到 {{ $value | humanizePercentage }}（阈值 5%），请立即核查模型 Provider 连通性或工作区权限。"

      # 2. 性能与延迟基线劣化告警
      - alert: AgentHubStepLatencyDegradation
        expr: histogram_quantile(0.99, sum(rate(agenthub_steps_duration_seconds_bucket[5m])) by (le, node_type)) > 30
        for: 5m
        labels:
          severity: warning
          team: ai-runtime
        annotations:
          summary: "AgentHub Step 节点 P99 延迟严重劣化"
          description: "节点类型 {{ $labels.node_type }} 的 P99 延迟超过 30 秒，当前观测值为 {{ $value }} 秒。"

      - alert: AgentHubWorkspaceLockContentionHigh
        expr: histogram_quantile(0.95, sum(rate(agenthub_workspace_lock_wait_seconds_bucket[5m])) by (le)) > 2
        for: 3m
        labels:
          severity: warning
          team: platform-core
        annotations:
          summary: "受控工作区租约锁竞争激烈"
          description: "工作区排他锁 P95 等待时长超过 2 秒，当前观测值为 {{ $value }} 秒，可能存在高频并发读写同一工作区情况。"

      # 3. 实时通信与长连接稳定性告警
      - alert: AgentHubSseConnectionDropSpike
        expr: delta(agenthub_sse_connections_active[2m]) < -50
        for: 1m
        labels:
          severity: warning
          team: gateway
        annotations:
          summary: "SSE 活跃长连接异常断崖式下跌"
          description: "2 分钟内断开活跃 SSE 连接数超过 50 个，可能存在 Nginx 网关重启、网络震荡或代理超时配置异常。"

      # 4. 外部大模型 Provider 熔断与限流告警
      - alert: AgentHubProviderCircuitBreakerOpen
        expr: agenthub_provider_circuit_breaker_state{state="OPEN"} == 1
        for: 1m
        labels:
          severity: critical
          team: ai-runtime
        annotations:
          summary: "大模型 Provider 触发熔断保护"
          description: "Provider {{ $labels.provider }} 连续遭遇 429 限流或 5xx 故障，已自动切断流量并转移至备用 Fallback Provider。"

      # 5. 存储与数据库连接池资源告警
      - alert: AgentHubHikariConnectionPoolSaturated
        expr: (hikaricp_connections_active / hikaricp_connections_max) > 0.85
        for: 2m
        labels:
          severity: critical
          team: dba
        annotations:
          summary: "MySQL 数据库连接池接近耗尽"
          description: "HikariCP 活跃连接占比达到 {{ $value | humanizePercentage }}（阈值 85%），请防范慢 SQL 或未关闭的长事务。"

      - alert: AgentHubDiskUsageCritical
        expr: agenthub_workspace_disk_avail_ratio{mountpoint="/var/lib/agenthub"} < 0.15
        for: 5m
        labels:
          severity: critical
          team: ops
        annotations:
          summary: "受控工作区存储磁盘空间不足"
          description: "挂载点 /var/lib/agenthub 可用空间不足 15%，请立即执行受控归档或清理过期构建缓存。"
```

---

## 3. 规则校验命令 (`promtool`)

```bash
docker run --rm -v "$(pwd)/docker/prometheus:/etc/prometheus:ro" \
  --entrypoint /bin/promtool prom/prometheus:v2.51.0 \
  check config /etc/prometheus/prometheus.yml
```

---

## 4. 告警分级与响应机制 (SLA)

| 级别 | 响应时限 (MTTA) | 解决时限 (MTTR) | 通知渠道 | 处理负责人 |
| :--- | :--- | :--- | :--- | :--- |
| **P0 (Critical)** | 5 分钟内 | 30 分钟内 | 电话呼叫 + 飞书/企业微信高危警报 | 值班 Tech Lead / 运维架构师 |
| **P1 (Warning)** | 15 分钟内 | 2 小时内 | 钉钉 / 邮件 / 团队群机器人 | 对应模块研发负责人 |
| **P2 (Info)** | 1 个工作日内 | 2 个工作日内 | 日常仪表盘 / 每日日报 | 日常巡检工程师 |
