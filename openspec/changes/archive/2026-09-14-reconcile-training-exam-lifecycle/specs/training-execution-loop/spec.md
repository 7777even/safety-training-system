## MODIFIED Requirements

### Requirement: 学员从培训上下文安全进入

系统 MUST 为当前登录学员提供本人任务、详情和结果，并从 participantId 与 contentId 派生课程报名或考试 Attempt。请求 MUST NOT 接受 userId、examId、assessmentExecutionId 或 contextType。培训 `EXAM` 仅在 `PENDING` 或 `NOT_STARTED` 时 MUST 先进入只读须知；`IN_PROGRESS` MUST 直接恢复作答；`QUESTION_BANK` MUST 直接进入练习。

#### Scenario: 学员进入本人任务

- **GIVEN** 当前用户是计划参训人且内容执行已生成
- **WHEN** 学员进入课程、考试或题库练习
- **THEN** 系统返回本人的 CourseEnrollment 或创建/恢复培训上下文 Attempt

#### Scenario: 访问他人任务

- **GIVEN** participantId 不属于当前登录用户
- **WHEN** 用户查询或进入内容
- **THEN** 系统返回 403 且不泄露他人资料和成绩

#### Scenario: 进行中考试恢复

- **WHEN** 学员进入 `IN_PROGRESS` 的培训考试
- **THEN** 客户端直接调用统一 enter 并恢复原 Attempt
- **AND** 不显示须知
