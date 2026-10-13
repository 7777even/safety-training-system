# Spec Delta：learner-exam-reading

## MODIFIED Requirements

### Requirement: 学员本人考试任务

系统 MUST 基于当前登录用户和 `sts_exam_candidate` 事实，按「我的考试」入口条件分页返回任务：Candidate 属于当前用户，且执行
`sourceType` 为独立考试来源（不是 `TRAINING_EXAM` 或 `TRAINING_PRACTICE`）。接口 MUST NOT 接受调用方指定 `userId`
。跨机构独立考试任务只要 Candidate 属于当前用户就 MUST 可见。培训来源考试 MUST 由「我的培训」入口进入。其他用户任务 MUST NOT
可见。

#### Scenario: 查询本人跨机构考试任务

- **GIVEN** 当前用户在主办机构发布的独立考试中存在 Candidate
- **WHEN** 用户查询学员考试任务
- **THEN** 系统返回该任务及其考试、上下文、当前 Attempt 和发布状态
- **AND** 不要求该用户属于主办机构成员

#### Scenario: 隔离其他用户任务

- **GIVEN** 数据库中同时存在当前用户和其他用户的 Candidate
- **WHEN** 当前用户查询学员考试任务
- **THEN** 结果只包含当前用户的 Candidate

#### Scenario: 我的考试入口不含培训考试

- **GIVEN** 当前用户同时存在独立考试 Candidate 和培训计划专属考试 Candidate
- **WHEN** 用户查询学员考试任务
- **THEN** 结果不包含 `sourceType` 为 `TRAINING_EXAM` 或 `TRAINING_PRACTICE` 的任务
- **AND** 独立考试任务仍返回

## ADDED Requirements

### Requirement: 学员按 Attempt 读取培训考试仍可用

系统 MUST 允许学员凭本人 `examAttemptId` 读取和继续作答培训来源考试。培训入口返回的 Attempt MUST 可用同一套作答能力。

#### Scenario: 从培训进入后读取 Attempt

- **GIVEN** 学员已通过培训入口获得计划专属考试的 Attempt
- **WHEN** 学员按该 `examAttemptId` 读取作答
- **THEN** 系统返回本人 Attempt
- **AND** 不得因执行来源为培训而拒绝
