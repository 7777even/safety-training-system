# Spec Delta：考试资源名称回显

## Requirement：考试查询 SHALL 返回可读资源名称

考试列表和详情响应 MUST 在存在关联资源时返回试卷名称与版本号，或题库名称；关联为空或资源不存在时名称字段 MUST 为 `null`。

### Scenario：管理员查看绑定试卷的考试

- **WHEN** 管理员查询绑定了试卷版本的考试
- **THEN** 响应包含 `paperName` 和 `paperVersionNo`，页面显示名称与版本号而非版本 ID

### Scenario：管理员查看绑定题库的练习

- **WHEN** 管理员查询 `BANK_PRACTICE` 考试
- **THEN** 响应包含 `questionBankName`，页面显示题库名称而非题库 ID
