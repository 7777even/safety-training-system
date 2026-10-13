# fill-blank-answering Specification

## Purpose

学员在 PC Web 或移动端作答填空题时，系统以空位为单位呈现、保存和判分，避免使用未约定的文本分隔符。

## ADDED Requirements

### Requirement: 学员作答快照提供安全的填空空位数量

作答快照中的填空题 MUST 返回 `blankCount`，值为冻结题目需填写的空位数量。该字段 MUST NOT 包含标准答案、答案候选或答案分组内容。非填空题的 `blankCount` MUST 为空。

#### Scenario: 两空题开始作答

- **GIVEN** 一道填空题的冻结快照有两个空位
- **WHEN** 学员加载尚未提交的作答
- **THEN** 题目返回 `blankCount=2`
- **AND** 正确答案快照仍不返回给学员。

#### Scenario: 历史单空快照

- **GIVEN** 一个历史填空题快照未保存 `blanks` 数组
- **WHEN** 学员加载作答
- **THEN** 系统返回 `blankCount=1`。

### Requirement: 填空题按空位输入和保存

PC Web 与移动端 MUST 为 `1..blankCount` 的每一个空位显示独立输入框。两端 MUST 以 `BLANK` answer item 保存非空输入，`itemKey` 和 `sortOrder` MUST 是从 `1` 开始的空位序号。学员 MUST NOT 被要求使用逗号、分号或其他拼接符。

#### Scenario: 暂存两空题

- **GIVEN** 一道题的 `blankCount=2`
- **WHEN** 学员填写第 1 空和第 2 空后暂存
- **THEN** 暂存请求包含两个 `BLANK` items，键分别为 `1` 和 `2`
- **AND** 题目状态为已答。

#### Scenario: 只填写部分空位

- **GIVEN** 一道题的 `blankCount=2`
- **WHEN** 学员只填写第 1 空并暂存
- **THEN** 已填写的 `BLANK` item 被保存
- **AND** 题目状态保持未答。
