# Design：填空题空位元数据与答案项对齐

| 层 | 输入 | 输出 / 责任 |
| --- | --- | --- |
| 后端作答快照 | 已冻结 `correctAnswerSnapshotJson.blanks` | 仅计算并返回 `blankCount`；不得返回 `blanks` 或正确答案 |
| PC Web、移动端 | `questionType=FILL`、`blankCount`、已保存 `BLANK` items | 按 `1..blankCount` 渲染并回填输入框 |
| 暂存接口 | 每个非空输入 | `itemType=BLANK`、`itemKey=空位序号`、`itemValue=输入文本`、`sortOrder=空位序号` |
| 自动判分 | `BLANK` items | 继续按 `itemKey` 与冻结答案中的 `blankNo` 比对 |

## 兼容规则

- 旧冻结快照没有 `blanks` 时，`blankCount` 返回 `1`，保持单空题可答。
- 空白输入不写入 `BLANK` item；只有全部 `blankCount` 个空位均有非空文本才标记题目已答。
- `answerText` 对填空题保持为空，不再作为多空拼接协议；简答题仍使用 `answerText`。

## 安全边界

`blankCount` 仅表达空位数量。学员作答中及结果未允许查看答案时，接口不得返回冻结答案 JSON 或答案候选值。
