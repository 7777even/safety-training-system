## ADDED Requirements

### Requirement: 培训进入不采用客户端作答类型

学员从培训上下文进入 EXAM/QUESTION_BANK 时，系统 MUST 只使用参训人身份、内容项、客户端令牌和设备信息。系统 MUST NOT 根据请求
`attemptType` 创建答卷。已有该考生非作废终态时 MUST 返回原 `examAttemptId`。补考 MUST 使用考试域补考接口，不得经 enter 声明
RETAKE。

#### Scenario: 培训考试交卷后 enter

- **GIVEN** 培训考试已有已提交或待发布答卷
- **WHEN** 学员再次 enter 并传 `attemptType=FIRST`
- **THEN** 返回既有 `examAttemptId`，不新增首次作答
