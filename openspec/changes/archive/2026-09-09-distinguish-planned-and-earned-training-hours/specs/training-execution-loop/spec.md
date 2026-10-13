## ADDED Requirements

### Requirement: 安排学时与获得学时不得混用

系统 MUST 将计划/模板内容（及阶段）上的 `requiredHours` 视为安排学时，仅用于发布时校验类型与标准的总学时和线上占比。COURSE、EXAM、QUESTION_BANK
的安排学时计入线上，OFFLINE 计入线下。系统 MUST 将逐内容 `effectiveDurationSeconds` 与参训人 `onlineHours`、`offlineHours`
视为获得学时：COURSE 为正式学习秒数，OFFLINE 为已确认且满足签退规则的有效秒数，EXAM/QUESTION_BANK MUST 为 0 且 MUST NOT 计入
`onlineHours`。QUESTION_BANK 执行配置 `durationSeconds` MUST 仅表示作答时限，MUST NOT 作为培训学时。计划结束 MUST NOT
用个人获得学时重算 TOTAL_HOURS 或线上占比。

#### Scenario: 发布按安排学时校验

- **GIVEN** 计划含课程、考试、题库和线下，各填了 requiredHours
- **WHEN** 管理员发布
- **THEN** 系统按内容 requiredHours 合计总学时，并将课程/考试/题库计入线上占比
- **AND** 不读取任何学员获得学时

#### Scenario: 考试完成后获得学时仍不含考试

- **GIVEN** 学员已完成培训考试且成绩已发布
- **WHEN** 查询参训结果
- **THEN** 该考试项 effectiveDurationSeconds 为 0
- **AND** onlineHours 仅含课程正式学习秒换算的小时
