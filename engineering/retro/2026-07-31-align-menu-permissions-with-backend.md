# Retro：菜单权限与后端授权点对齐

日期：2026-07-31

## 做得好的地方

- 先把人工审计固化为双向一致性测试，再修改种子数据，Red/Green 结果清晰。
- 同时覆盖空库 `menus.json` 和存量数据库 migration，避免新旧环境权限继续分叉。
- 明确默认 `admin` 与自定义角色的授权边界，没有借修复漏项扩大其他角色权限。

## 问题与原因

- 既有业务 Change 只在交付阶段人工检查菜单，多个业务域连续新增 Controller 权限后产生累计漂移。
- 菜单中保留了没有真实接口的历史占位按钮，缺少自动反向校验。
- 当前环境无 Docker socket，migration 真库 IT 无法执行。

## 改进

- 保留 `MenuPermissionConsistencyTest` 作为持续门禁，新增 Controller 权限必须同步菜单和 migration。
- 权限交付 Review 同时检查“后端未入菜单”和“菜单无后端能力”两个方向。
- Docker 可用后优先补跑 `MenuPermissionMigrationIT`，确认真实 MySQL 幂等和角色授权结果。
