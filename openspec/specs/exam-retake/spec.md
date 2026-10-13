# exam-retake Specification

## Purpose

考试补考是同一考生下的新作答尝试，不是新考试。窗口、范围、次数读取 `sts_exam_policy`；开补考接口只接受考生 ID 与幂等令牌。机器可读契约见
`docs/api/exam.openapi.json`。前端协作说明见 `docs/architecture/exam-retake-collaboration.md`。

## Requirements

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

### Requirement：补考开新尝试

`POST /api/exam/candidates/{examCandidateId}/retake` 仍只接受考生 ID 与客户端幂等令牌，并创建或恢复 `attempt_type=RETAKE`
的作答尝试。系统 MUST 在创建前执行本规格的策略护栏。通用开考 MUST NOT 以请求 `attemptType=RETAKE` 创建补考。

#### Scenario: 通用开考不得凭请求创建补考

- **GIVEN** 考生已有终态答卷且策略允许补考
- **WHEN** 学员调用通用 start 并传 `attemptType=RETAKE`
- **THEN** 系统不创建 RETAKE，返回已有答卷或按派生规则处理

### Requirement：普通开考由服务端派生作答类型

系统 MUST 忽略通用开考、培训进入、课程考核开考请求中的 `attemptType`。系统 MUST 按考生已有答卷决定恢复、返回终态或新建。新建时：可重复练习（课程题库、独立练习）为
`PRACTICE`；其余首次为 `FIRST`。系统 MUST NOT 因请求声明 `FIRST` 或 `RETAKE` 而插入对应类型。进行中答卷（`CREATED`/
`ANSWERING`）MUST 恢复，包括练习。

#### Scenario: 交卷后再次进入正式考试

- **GIVEN** 培训考试或独立考试考生已有非作废终态答卷
- **WHEN** 学员再次调用 start 或培训 enter，即使请求带 `attemptType=FIRST` 和新令牌
- **THEN** 系统返回已有终态答卷且不 insert

#### Scenario: 培训题库交卷后再次进入

- **GIVEN** `TRAINING_PRACTICE` 已有非作废终态
- **WHEN** 学员再次 enter
- **THEN** 系统返回该答卷且不创建第二份首次作答

#### Scenario: 无作答时创建首考

- **GIVEN** 考生没有非作废答卷
- **WHEN** 学员开考
- **THEN** 系统创建 `attemptType=FIRST` 的进行中答卷

#### Scenario: 课程题库可再练

- **GIVEN** `COURSE_BANK_PRACTICE` 已有终态且无进行中
- **WHEN** 学员再次开考
- **THEN** 系统创建新的 `PRACTICE` 答卷

### Requirement：作答详情必须返回服务端补考资格

作答详情 MUST 返回由服务端计算的补考资格、未满足原因及已用/最大补考次数。开补考写操作 MUST 复用同一规则并在写入前二次校验。补考
MUST
使用当前考试已经冻结、可用于生成作答快照的试卷和规则。

#### Scenario：资格读取后条件发生变化

- **GIVEN** 教师已看到可开补考状态
- **WHEN** 教师点击开补考前时间窗口、次数或考试状态发生变化
- **THEN** 服务端重新校验并拒绝不符合条件的创建，且不产生新的补考作答
