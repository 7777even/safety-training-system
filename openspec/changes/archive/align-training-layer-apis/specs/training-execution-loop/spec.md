# Spec Delta：training-execution-loop

## ADDED Requirements

### Requirement: 计划发布必须满足标准指定资源

若计划绑定了已发布培训标准，系统 MUST 在发布预检与发布时检查每条启用的 `COURSE`/`EXAM`/`OFFLINE` 规则：计划内容中存在相同内容类型与
`resourceId`。缺任一 MUST 使预检 `ready=false`，发布 MUST 失败并返回 400/`8156`。未绑定标准或标准无指定资源规则时 MUST NOT
因 8156 失败。

#### Scenario：缺少指定课程不得发布

- **GIVEN** 计划绑定的标准要求指定课程 A
- **AND** 计划内容中没有课程 A
- **WHEN** 调用发布预检或发布
- **THEN** 预检 `ready` 为 false 且 issues 含 8156
- **AND** 发布失败，计划仍为草稿

#### Scenario：内容已包含指定资源

- **GIVEN** 计划内容包含标准指定的课程、考试与线下
- **AND** 其他既有发布条件已满足
- **WHEN** 发布预检
- **THEN** 不得因指定资源产生 8156

### Requirement: 标准学时与占比不得宽于类型尺子

保存或发布培训标准时，启用的 `TOTAL_HOURS` MUST ≥ 所属培训类型已配置正数初培学时与复审学时中的最小值，否则 400/`8017`。启用的
`ONLINE_RATIO` 若类型配置了 `onlineMaxRatio`，MUST ≤ 该上限，否则 400/`8018`。

#### Scenario：标准总学时低于类型较小基线

- **GIVEN** 类型初培 80、复审 24
- **WHEN** 保存 TOTAL_HOURS=20 的启用规则
- **THEN** 系统返回 400/`8017`
- **AND** 规则不被写入

#### Scenario：标准线上占比宽于类型

- **GIVEN** 类型 onlineMaxRatio=0.3
- **WHEN** 保存 ONLINE_RATIO=0.5 的启用规则
- **THEN** 系统返回 400/`8018`

### Requirement: 模板发布不以本期执行配置为条件

系统 MUST 允许发布仅含内容引用、执行配置为空的模板。模板发布 MUST 要求至少有一个阶段和一个内容，否则 400/`8042`
。计划发布仍须校验计划内容执行配置。

#### Scenario：无执行配置的模板可发布

- **GIVEN** 模板有一个课程内容且无 executionConfig
- **WHEN** 发布模板
- **THEN** 模板状态为 PUBLISHED
