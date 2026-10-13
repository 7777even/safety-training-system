## ADDED Requirements

### Requirement: 绑卷时未提交的作答规则从试卷版本带出

当创建或草稿更新考试且请求包含存在的 `paperVersionId` 时，系统 MUST 把试卷版本上的 `durationSeconds`、`passRatio`、
`questionOrderMode`、`multiSelectScoring` 作为默认值写入考试行。请求省略的字段 MUST 使用试卷版本值。考试行写入后这些字段
MUST NOT 为 null。

#### Scenario: 创建考试只绑试卷版本

- **WHEN** 调用方创建考试并只提供 `paperVersionId` 及考试编码、名称、类型、状态等既有必填项，省略时长、及格比例、题序和多选计分
- **THEN** 系统读取该试卷版本对应字段写入考试，创建成功

#### Scenario: 绑定的试卷版本不存在

- **WHEN** 创建或更新考试时 `paperVersionId` 指向不存在的试卷版本
- **THEN** 系统返回 HTTP 400、业务码 `7005`，且不写入或更新考试规则字段

### Requirement: 提交的作答规则作为本场覆盖

当请求在绑卷同时提供时长、及格比例、题序或多选计分时，系统 MUST 使用请求值写入考试对应字段，MUST NOT 再用试卷版本值覆盖已提交字段。

#### Scenario: 创建时覆盖时长

- **WHEN** 调用方创建考试并绑定试卷版本，同时提交与试卷不同的 `durationSeconds`，省略其余规则字段
- **THEN** 考试时长等于请求值，及格比例、题序和多选计分等于试卷版本值

### Requirement: 换绑试卷时按新版本重填未提交字段

草稿考试更改 `paperVersionId` 时，系统 MUST 对未在请求中提交的规则字段从**新**试卷版本重填；请求提交的字段 MUST 保留为覆盖。将
`paperVersionId` 清空时，系统 MUST 按无试卷规则要求请求提供规则字段和及格分。

#### Scenario: 换绑且省略规则字段

- **WHEN** 草稿考试从试卷版本 A 换绑为版本 B，且请求省略时长、及格比例、题序和多选计分
- **THEN** 考试这四个字段等于版本 B 的值

#### Scenario: 换绑且提交时长

- **WHEN** 草稿考试换绑试卷版本，请求提交新的 `durationSeconds` 并省略其余规则字段
- **THEN** 考试时长等于请求值，其余规则字段等于新试卷版本值

### Requirement: 未换绑的更新不得把覆盖冲回默认值

草稿更新且 `paperVersionId` 未变化时，系统 MUST 将省略的规则字段保留为考试当前值，MUST NOT 再次从试卷版本带出。

#### Scenario: 更新名称时省略时长

- **WHEN** 已覆盖时长的草稿考试在不更换试卷版本的情况下更新名称，且请求省略 `durationSeconds`
- **THEN** 考试时长保持覆盖后的值

### Requirement: 无试卷时作答规则仍必填

当创建或更新考试且没有 `paperVersionId` 时，系统 MUST 要求 `durationSeconds`、`passRatio`、`questionOrderMode`、
`multiSelectScoring`；创建时 MUST 同时要求 `passScore`。缺失时 MUST 返回 HTTP 400、业务码 `1001`。

#### Scenario: 无试卷创建且省略时长

- **WHEN** 调用方创建不带 `paperVersionId` 的考试且省略 `durationSeconds`
- **THEN** 系统返回 HTTP 400、业务码 `1001`，且不创建考试

### Requirement: 有试卷时及格分按比例计算

当考试绑定试卷版本时，系统 MUST 忽略请求中的 `passScore`，并以 `试卷版本总分 × 生效及格比例` 计算考试 `passScore`（四舍五入到
2 位小数，与组卷相同）。生效及格比例在请求提交了 `passRatio` 时为请求值，否则为带出或考试原值按本 Change 其它规则得出的值。无试卷时系统
MUST NOT 按此公式覆盖手填及格分。

#### Scenario: 绑卷创建按试卷总分计算及格分

- **WHEN** 调用方绑定总分 100 的试卷版本，省略或提交 `passRatio=0.6`，并提交任意 `passScore`
- **THEN** 考试 `passScore` 为 60.00，而不是请求中的及格分

#### Scenario: 无试卷创建保留手填及格分

- **WHEN** 调用方创建不带 `paperVersionId` 的考试并提交 `passRatio` 与 `passScore=55`
- **THEN** 考试 `passScore` 为 55

### Requirement: 创建时可带出试卷封面且不得带出分类

创建考试且绑定试卷版本、请求未提供 `coverFileId` 时，系统 MUST 将对应试卷的 `coverFileId` 写入考试（试卷无封面则保持空）。系统
MUST NOT 把试卷分类 ID 写入考试分类 ID，MUST NOT 用试卷编码或名称覆盖考试编码或名称。更新考试 MUST NOT 因绑卷而自动改写已有封面。

#### Scenario: 创建时带出试卷封面

- **WHEN** 调用方创建考试并绑定有封面的试卷版本，且请求未提供 `coverFileId`
- **THEN** 考试 `coverFileId` 等于该试卷封面文件 ID，`examCategoryId` 仍为请求值

### Requirement: 开考仍使用考试生效规则

开始作答时，系统 MUST 继续把考试上的时长、及格分、题序和多选计分冻进尝试，MUST NOT 改为回读试卷版本上的对应字段。题目清单和总分仍来自试卷版本。

#### Scenario: 覆盖时长后开考

- **WHEN** 考试已覆盖时长并已发布，考生开始作答
- **THEN** 尝试的时长快照和截止时间使用考试时长，总分为所绑试卷版本总分
