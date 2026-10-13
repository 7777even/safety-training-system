# database-schema-delivery Specification

## Purpose

空库由 init 执行模块化完整 SQL 并登记 Flyway baseline；已有库由 Admin 只执行 Versioned Migration。两条路径必须得到相同最终结构。

## Requirements

### Requirement: init 使用模块化最新完整 SQL

系统 MUST 维护不含 `ALTER TABLE` 的模块化完整 SQL，由 init 仅对空库执行。

#### Scenario: 首次初始化空库

- **WHEN** init 连接到没有业务表的数据库
- **THEN** 系统按固定模块顺序执行全部最新完整 SQL
- **AND** 完整 SQL 中不包含 `ALTER TABLE` 或历史兼容步骤

### Requirement: Admin 只执行 Flyway Versioned Migration

init 完成完整建库后 MUST 登记当前 baseline；Admin MUST 只打包并执行 `V` 增量。

#### Scenario: Admin 接管 init 创建的数据库

- **GIVEN** init 已完成完整建库并登记当前 schema version 的 baseline
- **WHEN** Admin 启动 Flyway
- **THEN** Admin 跳过不高于 baseline 的版本并只执行更高版本的 pending `V` 脚本
- **AND** Admin 运行包不包含完整 SQL 或 `B` 脚本

### Requirement: 完整和增量路径同步

每次数据库变化 MUST 同时更新受影响的完整模块 SQL、新增 `V` 并更新 init schema version，两条路径最终结构 MUST 一致。

#### Scenario: 提交新的数据库结构变化

- **WHEN** 一个已确认 Change 修改数据库结构
- **THEN** 交付同时更新受影响的 init 完整模块、新增一个 Admin `V` 并将 init schema version 更新为该版本
- **AND** 从空库执行 init 与从上一版本执行 Admin 增量所得结构一致
