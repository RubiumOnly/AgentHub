-- ==============================================================================
-- V13__phase10_remove_demo_seed_subjects.sql (Phase 10 R3: 演示种子退出生产迁移路径)
-- ==============================================================================
-- Phase 10 收敛合同：生产启动不得存在固定主体、项目、工作区、Provider 或团队。
-- V2/V6/V7 注入的演示种子（user-1/user-dev、def-*、proj-default/ws-default、
-- prov-*、team-dev-swarm）自此迁移后从生产迁移路径归零；演示基线改由
-- demo/test profile 的夹具承载（测试套件中的 DemoBaselineSeedFixture）。
-- 本脚本只做 DML 删除，不改表结构；回滚方式为反向 INSERT（见 V2/V6/V7 原文）。

DELETE FROM team_members WHERE id IN ('tm-orch', 'tm-backend', 'tm-frontend', 'tm-qa');
DELETE FROM teams WHERE id = 'team-dev-swarm';
DELETE FROM providers WHERE id IN ('prov-openai', 'prov-deepseek', 'prov-anthropic', 'prov-gemini', 'prov-ollama', 'prov-mock');
DELETE FROM workspaces WHERE id = 'ws-default';
DELETE FROM projects WHERE id = 'proj-default';
DELETE FROM agent_definitions WHERE id IN ('def-orch', 'def-backend', 'def-frontend', 'def-qa');
DELETE FROM users WHERE id IN ('user-1', 'user-dev');
