# Spec Delta：培训计划列表信息展示

## ADDED Requirements

### Requirement：管理员可在培训计划列表识别安排信息

系统 MUST 在管理端培训计划列表展示每条计划的培训时间、培训地点和创建时间，且仅消费 `TrainingPlanVO` 既有字段。

#### Scenario：计划有完整安排信息

- **WHEN** 管理员查询到含 `startTime`、`endTime`、`location`、`createTime` 的培训计划
- **THEN** 列表显示格式化到分钟的培训时间和创建时间，以及培训地点

#### Scenario：地点或时间缺失

- **WHEN** 返回的地点为空，或任一时间为空
- **THEN** 对应展示 `-`，不得显示 `undefined`、`null` 或内部 ID
