# training-execution-loop Delta

## Requirement: 线下场次状态受控推进

系统 MUST 将线下场次 `sessionStatus` 限制为 `DRAFT`、`SCHEDULED`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`。手工创建 MUST 写入
`DRAFT`。计划生成执行 MUST 写入 `SCHEDULED`。普通更新 MUST NOT 修改 `sessionStatus`。

状态推进 MUST 仅通过：

- `start`：`SCHEDULED` → `IN_PROGRESS`
- `complete`：`IN_PROGRESS` → `COMPLETED`
- `cancel`：`SCHEDULED` 或 `IN_PROGRESS` → `CANCELLED`

非法转移 MUST 返回 409 / `8202`，不写库。版本冲突 MUST 返回 409 / `8204`。权限为 `training:plan:edit` 或
`training:teacher:task`；无计划编辑权限时 MUST 校验本场教师，否则 403 / `8131`。

#### Scenario: 开始已安排场次

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 本场教师或培训管理员调用 start 并回传当前版本
- **THEN** 状态变为 `IN_PROGRESS`

#### Scenario: 拒绝非法开始

- **GIVEN** 场次为 `IN_PROGRESS` 或 `COMPLETED`
- **WHEN** 调用 start
- **THEN** 返回 409 / `8202` 且状态不变

#### Scenario: 更新场次不改状态

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 调用普通更新
- **THEN** 名称地点等可改且 `sessionStatus` 仍为 `SCHEDULED`

## Requirement: 仅进行中场次可录分

系统 MUST 仅在场次 `IN_PROGRESS` 时接受线下成绩写入。其它状态 MUST 返回 409 / `8203`。教师任务 `availableActions` MUST
按状态给出 `START`/`CANCEL`（已安排）、`RECORD_SCORE`/`COMPLETE`/`CANCEL`（进行中），其它状态为空。

#### Scenario: 未开始不可录分

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 教师提交成绩
- **THEN** 返回 409 / `8203` 且不写成绩、不刷新投影
