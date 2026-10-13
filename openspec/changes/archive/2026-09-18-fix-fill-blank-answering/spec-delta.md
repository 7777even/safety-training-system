# Spec Delta：填空题 SHALL 按空位输入和保存

## Requirement：学员端 SHALL 获得安全的填空空位数量

包含填空题的学员作答快照 SHALL 返回 `blankCount`。该字段表示需填写的空位数，MUST NOT 包含标准答案、可接受答案或答案分组。

### Scenario：两空填空题开始作答

- **WHEN** 学员加载一题有两个冻结空位的填空题
- **THEN** 作答快照返回 `blankCount=2`
- **AND** 作答快照不返回 `correctAnswerSnapshotJson`。

## Requirement：填空题 SHALL 逐空提交

PC Web 与移动端 SHALL 为每个空位展示独立输入框，并以 `BLANK` item 的空位序号保存。系统 MUST NOT 要求学员输入逗号、分号或其他拼接符。

### Scenario：暂存两空填空题

- **WHEN** 学员分别填写第 1 空和第 2 空并暂存
- **THEN** 请求含 `itemKey="1"` 和 `itemKey="2"` 的两个 `BLANK` items
- **AND** 该题标记为已答。

### Scenario：只填写部分空位

- **WHEN** 学员只填写多空填空题的一部分空位
- **THEN** 已填写值可被暂存
- **AND** 该题保持未答状态。
