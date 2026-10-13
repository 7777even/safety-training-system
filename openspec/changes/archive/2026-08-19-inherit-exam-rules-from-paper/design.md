# Design：考试从试卷版本带入作答规则

## Context

试卷版本（`sts_paper_version`）在组卷时写入 `duration_seconds`、`pass_ratio`、`pass_score`、`question_order_mode`、
`multi_select_scoring`。考试头表（`sts_exam_exam`
）有同名列且创建/更新全部必填。开考快照从考试读取时长/及格分/题序/计分，从试卷版本读取题目和总分。库表设计把「发布时复制、明确覆盖才写考试值」标为暂定，产品未确认，实现变成两侧独立配置。

约束：考试绑的是不可变 `paper_version_id`，不是试卷当前稿；仅草稿可编辑；无试卷的考试类型仍需要本场规则；身份为 lims，只改后端与
OpenAPI。

## Goals / Non-Goals

**Goals:**

- 试卷版本是默认模板，考试行是本场生效值（继续 `NOT NULL`，带出结果物化）。
- 选卷/保存时带出，请求提交了则覆盖；不靠空值继承，不加覆盖标记列。
- 有试卷时及格分只按试卷总分 × 及格比例计算。
- 开考路径不变：仍读考试再生效冻到 `sts_exam_attempt`。

**Non-Goals:**

- 前端、Mock、联调。
- schema / migration / `isOverridden`。
- 拷贝分类、编码、名称。
- 修复 `BANK_PRACTICE`/`OFFLINE` 开考 `requirePaperVersion`。
- 从题库带出题序；改试卷组卷或考试策略。

## 系统设计

```
sts_paper_version          蓝图默认：时长 / 及格比例 / 及格分 / 题序 / 多选计分
        │  创建或草稿换绑且字段省略
        ▼
sts_exam_exam              本场生效值（NOT NULL，允许覆盖）
        │  开考（既有）
        ▼
sts_exam_attempt           冻结考试生效值 + 试卷题目与总分
```

规则字段集合 R：`durationSeconds`、`passRatio`、`questionOrderMode`、`multiSelectScoring`。`passScore` 不在 R 内，有试卷时由服务计算。

## 数据模型

无 migration。继续使用：

- `sts_paper_version`：默认规则与 `total_score`
- `sts_paper_paper.cover_file_id`：仅创建时封面为空才带出
- `sts_exam_exam`：本场生效规则，五字段保持 `NOT NULL`

不新增列。是否覆盖由「请求是否携带该字段」在当次写入时决定，不持久化标记。

## API 设计

不新增路径。修改 `POST /api/exam/exams`、`PUT /api/exam/exams/{examId}`。

权限仍为 `exam:exam:add` / `exam:exam:edit`。仅草稿可更新（既有 `7003`）。

`ExamCreateRequest` / `ExamUpdateRequest`：

- `paperVersionId` 仍可选。
- R 四个字段改为可省略（去掉创建/更新上的无条件 `@NotNull`/`@NotBlank` required）。
- `passScore`：有 `paperVersionId` 时请求值 MUST 被忽略；无试卷时创建必填、更新省略则保留原值。
- 无 `paperVersionId` 且 R 或（创建时的）`passScore` 缺失：HTTP 400，业务码 `1001`。
- `paperVersionId` 非空但版本不存在：HTTP 400，业务码 `7005`，不写库。

有试卷时 `passScore = paperVersion.totalScore × 生效 passRatio`，`RoundingMode.HALF_UP`、2 位小数，与组卷计算一致。

OpenAPI 必须写明省略语义、换绑重填、有试卷时及格分计算，并更新 required、字段注释和 example（含「只传
paperVersionId、省略规则字段」的创建 example）。

## 权限设计

不新增权限码。带出与覆盖在 Service 执行，不依赖前端是否预填。

## 技术方案

创建：

1. 校验 `examCode`/`examName`/`examType`/`status` 等既有必填。
2. 若 `paperVersionId == null`：R 与 `passScore` 必填；不访问试卷。
3. 若 `paperVersionId != null`：加载 `PaperVersion`，不存在则 `7005`。对 R 中每个字段：请求非空则用请求值，否则用版本值。
   `passScore` 按版本总分与生效比例计算。`coverFileId` 请求为空时，再读 `Paper.coverFileId` 写入。
4. 物化后写入考试行，字段不得为 null。

更新（仅草稿）：

1. 若 `paperVersionId` 相对当前考试**发生变化**（含从无到有、从有到无、换成另一版本）：
  - 新值为 null：R 与 `passScore` 按无试卷必填；不带出。
  - 新值非空：加载新版本；R 中省略的字段从**新版本**重填，提交的字段为覆盖；`passScore` 按新版本总分与生效比例重算。不自动覆盖已有
    `coverFileId`。
2. 若 `paperVersionId` **未变**：
  - R 中省略的字段保留考试原值（避免把既有覆盖冲回默认值）。
  - 提交的字段覆盖。
  - 若仍绑着试卷且 `passRatio` 有提交（或因覆盖导致比例变化），按当前绑定版本总分重算 `passScore`；比例未变则保留原
    `passScore`。
3. 其余字段（名称、开放时间、学分、须知等）仍按现更新语义整单写入。

绑定版本不要求此刻已 `PUBLISHED`；发布考试仍按既有规则要求已发布试卷版本（`7005`）。

开考、补考、判分不改：继续使用考试 `durationSeconds`/`passScore`/`questionOrderMode`/`multiSelectScoring`。

## Decisions

1. **物化到考试行，不用 null 表示继承。** 列表、筛选、开考冻结都要立刻知道本场多久。绑定的是不可变版本，不存在「试卷后来改了、未覆盖的考试要不要跟着变」。空值继承会把「这场多久」变成
   join。
2. **不加 `isOverridden`。** 换绑时「省略则重填、提交则覆盖」已够用。前端换卷清空 R 再提交即回到新默认值。
3. **带出在创建/换绑，不在发布。** 草稿表单必须先能看见生效值；发布时复制对调用方过晚。
4. **有试卷时忽略手填 `passScore`。** 与组卷一致，避免比例和分数互相矛盾。无试卷没有总分真相源，仍手填。
5. **更新未换绑时省略 = 保留原值，不是再带一次。** 否则前端整单更新漏传字段会清掉覆盖。
6. **封面只在创建且请求未给封面时带出。** 更新时空 `coverFileId` 不能区分「省略」和「清空」，避免误拷。

备选（否决）：

- 只听试卷：同一张卷无法开不同时长的场次；无试卷类型无处存放规则。
- 发布时复制：与当前创建必填冲突，草稿无法预览生效值。

## 风险和取舍

- [既有调用方同时传错误 `passScore`] → 有试卷后以计算值为准。OpenAPI 标明忽略。 Mitigation：契约 example 不再把独立及格分当作有试卷时的输入。
- [前端更新漏传 R] → 未换绑时保留原值，不会悄悄回到试卷默认。换绑且漏传则会重填，这是预期的「换卷重置未提交字段」。
- [草稿绑定未发布试卷版本] → 允许带出，发布仍拦截。与现发布护栏一致。
- [无 IT] → 无 schema 变化；零 DB 覆盖带出、覆盖、换绑、无试卷必填、版本不存在、及格分计算。

## Migration Plan

无数据库迁移。发布后旧考试行已有生效值，不必回填。回滚：恢复创建/更新必填与旧 Service 赋值即可，已保存的考试值仍合法。

## Open Questions

无。分类不带出、开考仍读考试、题库练习开考路径不在本 Change，均已排除。
