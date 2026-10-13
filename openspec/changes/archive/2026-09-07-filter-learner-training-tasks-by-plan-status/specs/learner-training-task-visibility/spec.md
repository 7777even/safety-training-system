# learner-training-task-visibility Specification Delta

## ADDED Requirements

### Requirement: 学员培训任务只暴露已对学员开放的计划

系统 MUST 仅把当前登录用户的参训记录，且所属培训计划 `publishStatus` 为 `PUBLISHED`、`RUNNING` 或 `ENDED` 的项，作为学员培训任务返回。
`DRAFT`、`CANCELLED`、`ARCHIVED` MUST NOT 出现在任务列表中。任务项 MUST 包含计划 `publishStatus`。

#### Scenario: 已发布或进行中的本人任务可见

- **GIVEN** 当前用户已被指派到 `PUBLISHED` 或 `RUNNING` 计划
- **WHEN** 查询 `GET /api/training/learner/tasks`
- **THEN** 返回对应参训任务
- **AND** `publishStatus` 分别为 `PUBLISHED` 或 `RUNNING`

#### Scenario: 已结束计划可作为历史任务查看

- **GIVEN** 当前用户是 `ENDED` 计划的参训人
- **WHEN** 查询学员任务或参训详情
- **THEN** 返回该任务及详情
- **AND** 统一进入内容仍因计划非 `PUBLISHED`/`RUNNING` 返回 409 / `8154`

#### Scenario: 草稿、取消和归档计划对学员不可见

- **GIVEN** 当前用户已被指派到 `DRAFT`、`CANCELLED` 或 `ARCHIVED` 计划
- **WHEN** 查询学员任务列表
- **THEN** 系统不返回这些计划的任务
- **AND** 用对应 `trainingParticipantId` 查询详情时返回 404 / `8155`，不返回计划编排内容

#### Scenario: 他人参训记录仍被拒绝

- **GIVEN** `trainingParticipantId` 不属于当前登录用户
- **WHEN** 查询参训详情
- **THEN** 系统返回 403 / `8150`
- **AND** 不得改用 `8155` 掩盖身份错误
