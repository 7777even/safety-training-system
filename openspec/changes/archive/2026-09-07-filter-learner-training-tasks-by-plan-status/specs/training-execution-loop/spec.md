# Spec Delta：training-execution-loop

## MODIFIED Requirements

### Requirement: 学员从培训上下文安全进入

系统 MUST 为当前登录学员提供本人任务、详情和结果，并从 participantId 与 contentId 派生课程报名或考试 Attempt。请求 MUST NOT
接受 userId、examId、assessmentExecutionId 或 contextType。学员任务与详情 MUST 仅覆盖计划
`publishStatus` 为 `PUBLISHED`、`RUNNING` 或 `ENDED` 的本人参训记录。

#### Scenario: 学员进入本人任务

- **GIVEN** 当前用户是 `PUBLISHED` 或 `RUNNING` 计划参训人且内容执行已生成
- **WHEN** 学员进入课程、考试或题库练习
- **THEN** 系统返回本人的 CourseEnrollment 或创建/恢复培训上下文 Attempt

#### Scenario: 访问他人任务

- **GIVEN** participantId 不属于当前登录用户
- **WHEN** 用户查询或进入内容
- **THEN** 系统返回 403 且不泄露他人资料和成绩

#### Scenario: 未对学员开放的计划不得作为任务返回

- **GIVEN** 当前用户已被指派，但计划为 `DRAFT`、`CANCELLED` 或 `ARCHIVED`
- **WHEN** 查询学员任务或参训详情
- **THEN** 列表不包含该计划，详情返回 404 / `8155`
