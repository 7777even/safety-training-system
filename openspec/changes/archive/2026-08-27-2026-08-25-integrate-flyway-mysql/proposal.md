# Proposal：init 完整建库与 Admin Flyway 增量

> 状态：已于 2026-08-25 经人工确认并完成实施。

## 背景

系统尚未上线，不需要兼容开发期数据库。首次部署由独立 init CLI 建立完整数据库，长期运行的 Admin 只负责后续增量升级。

## 目标

1. init 使用按业务模块拆分的最新完整 SQL，只表达最终结构，不包含 `ALTER TABLE`。
2. init 建库后登记 Flyway baseline，再写入种子数据并退出。
3. Admin 集成 Flyway MySQL，运行包只携带 `V` 增量脚本。
4. 每次数据库变化同步更新完整模块 SQL、新增 `V` 并更新 init schema version。

## 范围

- `backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` 模块化完整建库资产。
- `backend/sts-service-admin/src/main/resources/db/migration/V*.sql` Admin 增量资产。
- init 建库、baseline、种子顺序；Admin Flyway 配置和资源打包。
- MySQL IT、规则、部署、QA 与 Retro。

## 非目标范围

- 迁移或保留当前开发数据库数据。
- 自动接管未知非空数据库。
- 前端、API 或业务能力变更。
