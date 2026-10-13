# training-standard-singleton-rules Specification

## Purpose

培训标准的总学时和线上学时占比规则必须可由后端判定为单值，不能依赖前端限制。

## ADDED Requirements

### Requirement: 总学时和线上占比规则各只能有一条

同一培训标准的有效规则中，`TOTAL_HOURS` MUST 最多一条，`ONLINE_RATIO` MUST 最多一条。计数包含禁用规则，不包含已逻辑删除规则。
`COURSE`、`EXAM`、`OFFLINE` MUST 仍允许多条。系统 MUST 在整体替换规则和发布标准时都执行该约束。

#### Scenario: 替换规则写入各一条基础规则

- **GIVEN** 当前标准为草稿
- **AND** 请求包含一条 `TOTAL_HOURS`、一条 `ONLINE_RATIO` 和任意条指定资源规则
- **WHEN** 管理员整体替换规则
- **THEN** 系统保存请求中的全量规则

#### Scenario: 替换规则拒绝重复总学时

- **GIVEN** 当前标准为草稿
- **WHEN** 请求包含两条 `TOTAL_HOURS` 规则
- **THEN** 系统返回 HTTP 409 和业务码 `8016`
- **AND** 错误消息说明总学时规则只能配置一条
- **AND** 既有规则与乐观锁版本保持不变

#### Scenario: 替换规则拒绝重复线上占比

- **GIVEN** 当前标准为草稿
- **WHEN** 请求包含两条 `ONLINE_RATIO` 规则
- **THEN** 系统返回 HTTP 409 和业务码 `8016`
- **AND** 错误消息说明线上学时占比规则只能配置一条
- **AND** 既有规则与乐观锁版本保持不变

#### Scenario: 清空规则允许

- **GIVEN** 当前标准为草稿
- **WHEN** 整体替换提交空规则列表
- **THEN** 系统删除当前有效规则
- **AND** 不因缺少 `TOTAL_HOURS` 或 `ONLINE_RATIO` 而失败

#### Scenario: 发布拒绝已存在的重复基础规则

- **GIVEN** 草稿标准当前有效规则中存在两条 `TOTAL_HOURS` 或两条 `ONLINE_RATIO`
- **WHEN** 管理员发布该标准
- **THEN** 系统返回 HTTP 409 和业务码 `8016`
- **AND** 标准保持草稿
