# Tasks：init 完整建库与 Admin Flyway 增量

> 状态：已于 2026-08-25 经人工确认并完成实施。
>
> Owner：`lims`。仅后端；前端以 OpenAPI 为准另开。

## Task 1：收口模块化完整 SQL 与增量目录

- 状态：已完成
- Owner：`lims`
- 输入：原模块 schema、开发期 migration、MySQL 8.0 DDL 规约。
- 输出：`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` 最新完整结构；
  `backend/sts-service-admin/src/main/resources/db/migration/V*.sql` Admin 增量；删除根 `database/` 和旧开发期增量。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：无。
- 数据影响：开发/测试库重新初始化，不保留原数据。
- 验收标准：13 个模块 SQL齐全、可独立完成空库建库且不包含 `ALTER TABLE`；Admin 目录当前可无增量，未来 SQL 只允许 `V`。
- 测试方式：资源测试、SQL 静态检查、MySQL 8.0 空库建库。

## Task 2：完成 init baseline 与 Admin 增量接管

- 状态：已完成
- Owner：`lims`
- 输入：Task 1 SQL；init 和 Admin 数据源配置。
- 输出：init 执行完整 SQL → Flyway baseline → 种子；Admin Spring Flyway 只执行 pending `V`。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：无。
- 数据影响：init 创建 `flyway_schema_history` baseline 记录。
- 验收标准：init 只允许空库且执行一次；Admin 不重复建库；未知非空库不自动 baseline。
- 测试方式：零 DB 编排测试、init MySQL IT、Admin Flyway MySQL IT。

## Task 3：完成测试、规则和工程收口

- 状态：已完成
- Owner：`lims`
- 输入：Task 1～2。
- 输出：Admin IT 建库统一复用 init 完整 SQL；规则、架构、部署、QA、Retro 和进度同步。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：无。
- 数据影响：后续固定交付“更新完整模块 SQL + 新增 V + 更新 schema version”。
- 验收标准：init/Admin 交接、Admin 持久化回归、打包资源和 OpenSpec strict 均通过。
- 测试方式：目标零 DB、完整 MySQL IT、包内容检查、`git diff --check`、双轴 Review。

## 实施结果

- init 已通过 13 个完整模块 SQL 建库，并登记 baseline `20260811150000` 后写入种子。
- 人员选择简化的最终结构已吸收到完整 SQL，对应上线前旧库转换 `V` 已删除；Admin 当前没有增量 SQL，baseline 后 migrate 执行数为
  0。
- 根 `database/`、Flyway `B` 和上线前旧增量已删除；可从 Git 恢复。
