# training-api-contract Specification

## ADDED Requirements

### Requirement: 学员参训详情返回参训人对象和计划对象

`GET /api/training/learner/participants/{trainingParticipantId}` 的 `data.participant` MUST 为 `TrainingParticipantVO`
对象，`data.plan` MUST 为 `TrainingPlanVO` 对象。`plan.publishStatus` MUST 返回，且对本接口仅为 `PUBLISHED`、`RUNNING` 或
`ENDED`。契约 MUST NOT 将这两个字段描述为字符串。

#### Scenario: 查询可见计划的学员详情

- **GIVEN** 当前用户是某 `PUBLISHED`、`RUNNING` 或 `ENDED` 计划的参训人
- **WHEN** 查询本人参训详情
- **THEN** `participant` 为参训人对象并包含 `trainingParticipantId`
- **AND** `plan` 为计划对象
- **AND** `plan.publishStatus` 等于该计划当前发布状态

#### Scenario: 契约与实现类型一致

- **GIVEN** 调用方依据 `docs/api/training.openapi.json` 生成学员详情类型
- **WHEN** 读取 `TrainingLearnerParticipantDetailVO`
- **THEN** `participant` 引用 `TrainingParticipantVO`
- **AND** `plan` 引用 `TrainingPlanVO`
- **AND** 成功响应 example 使用嵌套对象而不是字符串
