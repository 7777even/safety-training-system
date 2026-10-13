# Spec Delta：批阅意见读取与教师批阅界面优化

## MODIFIED Requirements

### Requirement：管理端作答详情

系统 MUST 为具有 `exam:attempt:list` 权限的内部用户返回作答详情及每道答案最近一次批阅意见。

#### Scenario：教师刷新后回显意见

- **GIVEN** 教师已为一道主观题保存批阅意见“要点完整”
- **WHEN** 教师重新读取该作答详情
- **THEN** 对应 `questions[].answer.reviewComment` 返回“要点完整”
- **AND** 教师批阅页在意见输入框中回显该值

#### Scenario：多次批阅读取最近意见

- **GIVEN** 同一答案存在多条追加式批阅记录
- **WHEN** 管理端读取作答详情
- **THEN** 仅返回按批阅时间和记录主键确定的最近一条记录的意见

### Requirement：学员作答详情字段裁剪

系统 MUST 不向学员本人作答详情暴露内部批阅意见。

#### Scenario：成绩已发布仍裁剪意见

- **GIVEN** 作答已完成批阅且成绩已发布
- **WHEN** 学员读取自己的作答详情
- **THEN** `questions[].answer.reviewComment` 为 `null`
- **AND** 不返回内部批阅意见文本
