# Spec Delta：补考策略护栏

## ADDED Requirements

### Requirement：补考必须命中考试策略

系统 MUST 仅在考试策略启用补考时允许开新补考尝试。未启用 MUST 返回业务码 `7051`。

### Requirement：补考范围

系统 MUST 按 `retake_scope` 限制可补考考生。空白范围视为 `FAILED`。`FAILED` 要求正式成绩已发布且考生不及格。`ABSENT` 仅未参加。
`FAILED_OR_ABSENT` 允许不及格或未参加。其他状态 MUST 返回 `7055`。

### Requirement：补考开放窗口

系统 MUST 在配置了补考开始或结束时间时按闭区间校验当前时间。窗口外 MUST 返回 `7054`。未配置的一端不限制。正考开放期和迟到禁考
MUST NOT 用于拒绝补考。

### Requirement：补考次数

系统 MUST 统计该考生非 `VOID` 的 `RETAKE` 尝试数，达到 `retake_max_count` 后拒绝新补考并返回 `7052`。首考不计入补考次数。

### Requirement：考试结束后补考

`PUBLISHED`、`RUNNING`、`ENDED` 的考试允许补考。`DRAFT` 或 `CANCELLED` MUST 返回 `7056`。

### Requirement：进行中补考恢复

同一考生若已有幂等令牌匹配或 `CREATED`/`ANSWERING` 尝试，系统 MUST 恢复该尝试，不得再开新补考次数。

## MODIFIED Requirements

### Requirement：补考开新尝试

`POST /api/exam/candidates/{examCandidateId}/retake` 仍只接受考生 ID 与客户端幂等令牌，并创建或恢复 `attempt_type=RETAKE`
的作答尝试。系统 MUST 在创建前执行本 Change 的策略护栏；通用开考以 `attemptType=RETAKE` 调用时 MUST 使用同一护栏。
