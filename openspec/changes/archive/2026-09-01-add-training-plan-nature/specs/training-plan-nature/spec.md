# training-plan-nature Specification

## Purpose

培训计划明确选择初培或复审，按培训类型对应学时及不可降低的培训标准完成发布校验，发布后冻结判定口径，并让监管预警使用同一份可追溯事实。

## ADDED Requirements

### Requirement: 培训计划必须明确初培或复审

系统 MUST 将培训类型和培训性质作为两个独立业务维度。新建或修改草稿计划时，`trainingNature` MUST 为 `INITIAL` 或
`REVIEW`；`UNSPECIFIED` MUST 仅用于迁移前历史计划，MUST NOT 作为新请求的合法业务值。

#### Scenario: 创建初培计划

- **GIVEN** 当前机构存在可用培训类型
- **WHEN** 管理员创建计划并提交 `trainingNature=INITIAL`
- **THEN** 系统保存该计划为初培计划
- **AND** 查询详情和分页结果返回 `INITIAL`

#### Scenario: 创建请求没有培训性质

- **GIVEN** 管理员提交培训计划创建请求
- **WHEN** 请求缺少 `trainingNature` 或提交 `UNSPECIFIED`
- **THEN** 系统返回 HTTP 400 和业务码 1001
- **AND** 不创建培训计划

#### Scenario: 历史计划保持未知

- **GIVEN** 计划创建于本能力上线之前且没有可靠的初培 / 复审事实
- **WHEN** migration 完成或调用方查询该计划
- **THEN** 系统保存并返回 `UNSPECIFIED`
- **AND** 不依据名称、内容学时、培训类型或培训标准猜测其性质

### Requirement: 培训类型必须提供两套学时基线

培训类型的新建和修改请求 MUST 提供大于零的 `initialHours` 与 `reviewHours`。所有产品契约 MUST 使用“初培要求学时”和“复审要求学时”描述
二者，不得把 `reviewHours` 另述为“复训”或“初审”。

#### Scenario: 保存完整培训类型

- **GIVEN** 管理员具有培训类型维护权限
- **WHEN** 提交正数初培学时和复审学时
- **THEN** 系统保存两套独立基线

#### Scenario: 缺少复审学时

- **GIVEN** 管理员新建或修改培训类型
- **WHEN** `reviewHours` 为空、为零或为负数
- **THEN** 系统返回 HTTP 400 和业务码 1001
- **AND** 不保存不完整配置

### Requirement: 发布按培训性质确定最低学时

初培计划 MUST 使用培训类型 `initialHours`，复审计划 MUST 使用 `reviewHours`。若计划选择培训标准，全部启用的
`TOTAL_HOURS` 要求 MUST 只能收紧培训类型基线；`applicableRequiredHours` MUST 为对应类型基线和标准要求的最大值。计划内容
`requiredHours` 合计低于该值时 MUST NOT 发布。

#### Scenario: 同一类型发布初培和复审计划

- **GIVEN** 培训类型初培学时为 80、复审学时为 24
- **AND** 两个草稿计划分别选择 `INITIAL` 和 `REVIEW`
- **WHEN** 系统执行发布预检
- **THEN** 初培计划最低要求为 80
- **AND** 复审计划最低要求为 24

#### Scenario: 培训标准提高最低学时

- **GIVEN** 初培类型基线为 80
- **AND** 已发布培训标准包含启用的 `TOTAL_HOURS=96`
- **WHEN** 系统预检初培计划
- **THEN** `applicableRequiredHours` 为 96

#### Scenario: 培训标准低于类型基线

- **GIVEN** 初培类型基线为 80
- **AND** 已发布培训标准包含启用的 `TOTAL_HOURS=60`
- **WHEN** 系统预检初培计划
- **THEN** `applicableRequiredHours` 仍为 80
- **AND** 培训标准不得把类型基线降低到 60

#### Scenario: 计划学时不足

- **GIVEN** 复审计划最低要求为 24，计划内容要求学时合计为 20
- **WHEN** 管理员预检或发布计划
- **THEN** 系统返回学时问题码 8121，说明培训性质、实际学时和最低要求
- **AND** 不写发布快照且计划保持 DRAFT

#### Scenario: 对应类型学时未配置

- **GIVEN** 历史培训类型没有可用的复审学时
- **AND** 草稿计划选择 `REVIEW`
- **WHEN** 管理员发布计划
- **THEN** 系统返回 HTTP 400 和业务码 8129
- **AND** 不使用初培学时、标准学时或默认值替代复审学时

### Requirement: 发布冻结培训性质与适用学时

发布成功时系统 MUST 在计划关系型字段和 v2 类型快照中同时冻结 `trainingNature` 与 `applicableRequiredHours`，并纳入既有
`sourceSnapshotHash`。已发布计划 MUST NOT 因来源培训类型或标准后续修改而改变判定口径。

#### Scenario: 来源学时在发布后变化

- **GIVEN** 初培计划以 80 学时成功发布
- **WHEN** 来源培训类型的 `initialHours` 后续调整为 96
- **THEN** 已发布计划的 `applicableRequiredHours` 仍为 80
- **AND** 类型快照和 hash 保持不变

#### Scenario: 冻结列与快照一致

- **GIVEN** 复审计划通过发布校验
- **WHEN** 发布事务提交
- **THEN** 计划列和 v2 类型快照包含相同的 `REVIEW` 与适用最低学时
- **AND** 任一快照写入或计划版本更新失败时整个发布事务回滚

### Requirement: 监管预警使用计划冻结口径

对具有冻结适用学时的新发布计划，`PLAN_TOTAL_HOURS` 检测 MUST 使用
`max(plan.applicableRequiredHours, condition.thresholdValue)` 作为最终预警阈值；MUST NOT 读取培训类型或标准当前值重新解释历史计划。证据
MUST 记录培训性质、计划总学时、冻结最低学时、条件阈值、最终阈值及阈值来源。

#### Scenario: 初培和复审产生不同预警结果

- **GIVEN** 同一培训类型的初培最低学时为 80、复审最低学时为 24
- **AND** 两个计划的内容学时均为 30
- **WHEN** 执行 `PLAN_TOTAL_HOURS` 检测
- **THEN** 初培计划触发学时不足预警
- **AND** 复审计划不因培训类型基线触发学时不足预警
- **AND** 两份检测证据明确记录各自培训性质和阈值

#### Scenario: 规则条件只收紧冻结要求

- **GIVEN** 计划冻结最低学时为 80
- **WHEN** 规则条件阈值分别为 60 和 96
- **THEN** 最终阈值分别为 80 和 96
- **AND** 条件阈值不得把冻结要求降低为 60

#### Scenario: 来源配置修改不影响预警

- **GIVEN** 计划已冻结初培最低学时 80
- **WHEN** 来源培训类型或标准后来改变且再次执行检测
- **THEN** 检测仍以 80 作为计划冻结基线

#### Scenario: 历史未知计划兼容检测

- **GIVEN** 历史计划的性质为 `UNSPECIFIED` 且没有冻结适用学时
- **WHEN** 执行 `PLAN_TOTAL_HOURS` 检测
- **THEN** 系统沿用培训标准 `TOTAL_HOURS`，没有标准值时使用规则条件阈值
- **AND** 证据将阈值来源标记为 `LEGACY_FALLBACK`
- **AND** 系统不选择 `initialHours` 或 `reviewHours`

### Requirement: 培训性质数据库变更可复现

培训计划性质与冻结学时结构 MUST 同时存在于 init 最终 SQL 和新的 Admin Versioned Migration，且 MUST 同步
`sts.init.schema-version`。既有 Versioned Migration MUST NOT 被修改、重命名或删除。

#### Scenario: 空库与升级库获得一致结构

- **GIVEN** 一个空库和一个包含既有培训计划的升级前数据库
- **WHEN** 分别执行 init 和 Flyway migration
- **THEN** 两者具有一致的 `training_nature` 与 `applicable_required_hours` 列定义
- **AND** 升级库历史计划为 `UNSPECIFIED` 且适用最低学时为空
- **AND** 新计划只能通过应用契约写入 `INITIAL` 或 `REVIEW`
