## ADDED Requirements

### Requirement: 普通开考由服务端派生作答类型

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

## MODIFIED Requirements

### Requirement: 补考开新尝试

`POST /api/exam/candidates/{examCandidateId}/retake` 仍只接受考生 ID 与客户端幂等令牌，并创建或恢复 `attempt_type=RETAKE`
的作答尝试。系统 MUST 在创建前执行本规格的策略护栏。通用开考 MUST NOT 以请求 `attemptType=RETAKE` 创建补考。

#### Scenario: 通用开考不得凭请求创建补考

- **GIVEN** 考生已有终态答卷且策略允许补考
- **WHEN** 学员调用通用 start 并传 `attemptType=RETAKE`
- **THEN** 系统不创建 RETAKE，返回已有答卷或按派生规则处理

