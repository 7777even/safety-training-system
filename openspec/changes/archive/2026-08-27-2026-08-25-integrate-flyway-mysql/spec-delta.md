# Spec Delta：init 完整 SQL 与 Admin Flyway 增量

## ADDED Requirements

### Requirement: init 必须使用模块化最新完整 SQL

系统 MUST 在 `backend/sts-service-admin-init/src/main/resources/schema/` 按业务模块维护最新完整 SQL。完整 SQL MUST
直接表达最终结构，MUST NOT 包含 `ALTER TABLE`。

#### Scenario: 初始化空数据库

- **GIVEN** 一个空 MySQL 8.0 数据库
- **WHEN** 执行一次性 init CLI
- **THEN** 系统按固定顺序执行全部模块完整 SQL
- **AND** 数据库得到当前完整结构

### Requirement: init 必须登记 Admin 可接管的 baseline

init MUST 在完整建库后使用 `sts.init.schema-version` 登记 Flyway baseline，并在 baseline 成功后写种子。

#### Scenario: Admin 接管 init 数据库

- **GIVEN** init 已完成完整建库和 baseline
- **WHEN** Admin 首次启动
- **THEN** Flyway 只执行 baseline 版本之后 pending 的 `V`
- **AND** 不重复执行完整建库 SQL

### Requirement: 数据库变化必须同步完整与增量路径

每次数据库变化 MUST 更新受影响的 init 完整模块 SQL，MUST 新增 Admin `V`，并 MUST 同步 `sts.init.schema-version`。

#### Scenario: 新增业务表

- **GIVEN** 当前 schema version 已确定
- **WHEN** 新增一个业务表
- **THEN** 对应 init 模块 SQL直接包含该表的最终 `CREATE TABLE`
- **AND** Admin 新增同版本 `V` 为已有数据库创建该表
- **AND** 两条路径最终结构一致

### Requirement: Admin 不得自动接管未知数据库

Admin MUST 禁用 `baseline-on-migrate`。未知非空数据库 MUST 阻止迁移。

#### Scenario: 未经 init 的非空数据库

- **GIVEN** 数据库非空且没有 `flyway_schema_history`
- **WHEN** Admin 启动
- **THEN** Flyway 失败并阻止业务服务启动
