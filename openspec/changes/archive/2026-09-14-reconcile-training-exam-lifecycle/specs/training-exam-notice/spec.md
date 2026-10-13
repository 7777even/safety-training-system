## ADDED Requirements

### Requirement: 培训理论考试须知只读读取

系统 MUST 允许当前登录参训人在运行中培训计划读取本人 `EXAM` 内容的计划专属考试须知，且读取不得创建或恢复 Attempt。

#### Scenario: 待开始考试读取须知

- **WHEN** 学员读取待开始 `EXAM` 内容的须知
- **THEN** 返回考试快照展示字段且不产生作答记录
