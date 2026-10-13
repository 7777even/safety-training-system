# Design：考试资源名称回显

`ExamVO` 增加三个可空的只读展示字段：

- `paperName`：`paperVersionId` 对应 `PaperVersion.paperId` 的 `Paper.paperName`。
- `paperVersionNo`：对应 `PaperVersion.versionNo`。
- `questionBankName`：`questionBankId` 对应 `QuestionBank.bankName`。

`ExamServiceImpl` 在组装 VO 时按当前关联资源读取名称；关联为空或历史资源已不存在时字段为 `null`，前端显示“未设置”，不得显示内部 ID。名称不写入考试表，因此资源改名后下一次查询即时反映。

列表展示考试类型中文文案，并按资源类型展示“试卷名称（版本 N）”或题库名称；开放时间分别展示开始与结束时间。
