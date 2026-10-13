# Spec Delta：题库练习与线下考试 SHALL 不要求批阅人

## Requirement：独立题库练习 SHALL 与培训题库练习保持不评分语义

独立 `BANK_PRACTICE` SHALL 对简答题仅记录作答，不评分、不批阅；其他题型 SHALL 提供自动判定反馈。该练习 SHALL NOT 生成正式分数、合格结论或待批阅状态。

### Scenario：题库练习抽到简答题

- **WHEN** 学员提交独立题库练习
- **THEN** 简答题状态为 `NOT_EVALUATED`
- **AND** 尝试 SHALL NOT 进入 `PENDING_REVIEW` 或 `PENDING_PUBLISH`。

## Requirement：发布门禁 SHALL 仅要求实际需要的人员配置

`BANK_PRACTICE` 与 `OFFLINE` 发布时 SHALL 要求至少一名考生，MUST NOT 要求批阅人。批阅人仅用于人工批阅线上/专项考试的主观题。

### Scenario：无批阅人的题库练习发布

- **WHEN** 管理员已配置题库练习的抽题规则和考生，但未配置批阅人
- **THEN** 管理端 SHALL 允许发布。

### Scenario：线下考试录分

- **WHEN** 有 `exam:score:publish` 权限的人员录入线下成绩
- **THEN** 系统 SHALL 录入并发布成绩，且不依赖批阅人名单。
