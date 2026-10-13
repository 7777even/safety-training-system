# Spec Delta：四类考试类型闭环缺口补齐

## Requirement：线下考试成绩 SHALL 由管理端录入并驱动学员端展示

线下考试（`examType=OFFLINE`）MUST NOT 允许学员在线作答；其成绩 MUST 由管理端录入，并作为学员端展示成绩与
合格状态的唯一来源。

### Scenario：学员查看线下考试任务

- **WHEN** 学员访问「我的考试」且其中包含线下考试
- **THEN** 系统 SHALL 保留该考试在列表中
- **AND** 未录入成绩时 SHALL NOT 返回可执行动作（`availableAction` 为空）
- **AND** 已录入成绩时 SHALL 返回 `VIEW_RESULT` 动作
- **AND** MUST NOT 返回 `START` 动作。

### Scenario：学员尝试直接开考线下考试

- **WHEN** 客户端对 `examType=OFFLINE` 的考试调用开始作答接口
- **THEN** 系统 SHALL 拒绝请求并返回业务码 7072「线下考试不支持在线作答」
- **AND** MUST NOT 写入任何作答尝试或作答题目快照
- **AND** MUST NOT 通过考试 ID 查询试卷版本题目。

### Scenario：管理端录入线下成绩

- **WHEN** 管理端提交某线下考试若干考生的成绩
- **THEN** 系统 SHALL 校验每个分数落在 `[0, 满分]` 区间，任一越界 SHALL 整批拒绝并返回 7068
- **AND** SHALL 校验每个考生属于该考试，否则返回 7065
- **AND** 对非线下考试 SHALL 返回 7069
- **AND** 对草稿状态考试 SHALL 返回 7006
- **AND** 校验通过后 SHALL 更新考生成绩、判定合格状态并写入成绩发布与生效时间。

### Scenario：重复录入同一考生成绩

- **WHEN** 管理端对同一考生再次提交成绩
- **THEN** 系统 SHALL 覆盖既有成绩
- **AND** MUST NOT 新增作答尝试记录
- **AND** 学员端查询 SHALL 返回最新成绩。

## Requirement：题库练习 SHALL 按抽题规则冻结题目

独立题库练习（`examType=BANK_PRACTICE` 且不属于培训计划）MUST 按管理端配置的抽题规则抽取题目，
MUST NOT 将题库全部已发布题目全量冻结。

### Scenario：携带抽题规则发布题库练习

- **WHEN** 管理端为一个 `BANK_PRACTICE` 考试配置了抽题规则并发布
- **THEN** 系统 SHALL 把规则冻结进考核执行快照
- **AND** 发布后学员开考时 SHALL 按规则抽取题目
- **AND** 执行的满分快照 SHALL 等于各规则 `抽题数 × 每题分值` 之和。

### Scenario：未配置抽题规则

- **WHEN** 管理端发布一个未配置抽题规则的 `BANK_PRACTICE` 考试
- **THEN** 系统 SHALL 拒绝发布并返回业务码 7063「题库练习未配置抽题规则」
- **AND** MUST NOT 生成执行快照。

### Scenario：及格分超过抽题总分

- **WHEN** 管理端创建或更新 `BANK_PRACTICE` 考试，且及格分大于配置的抽题总分
- **THEN** 系统 SHALL 拒绝并返回业务码 7063
- **AND** 冻结快照中的及格分 SHALL NOT 超过该次抽题的满分。

### Scenario：抽题候选不足

- **WHEN** 某条规则的候选题目数量少于配置抽题数
- **THEN** 系统 SHALL 拒绝开考并返回业务码 7063，且提示不足的规则序号与题型。

## Requirement：答案详情可见性 SHALL 完整实现契约枚举

`answerDetailMode` 的契约取值 `SUBMITTED`、`PUBLISHED`、`NEVER` MUST 全部在后端生效。

### Scenario：成绩发布后可见

- **WHEN** 考试的 `answerDetailMode` 为 `PUBLISHED` 且学员的作答成绩已发布
- **THEN** 学员端作答详情 SHALL 返回标准答案、选项正确标记与题目解析
- **AND** SHALL NOT 因该枚举值缺失而退化为不可见。

### Scenario：交卷后可见

- **WHEN** 考试的 `answerDetailMode` 为 `SUBMITTED`
- **THEN** 学员端作答详情 SHALL 返回标准答案、选项正确标记与题目解析。

### Scenario：始终不可见

- **WHEN** 考试的 `answerDetailMode` 为 `NEVER` 或成绩尚未发布
- **THEN** 学员端作答详情 SHALL NOT 返回标准答案、选项正确标记与题目解析
- **AND** SHALL 隐藏分数字段的同时保留题目列表与学员自己的作答内容。

## Requirement：考试类型文案 SHALL 跨端一致且不暴露枚举码

`examType` 的中文文案 MUST 由 `shared` 工具统一提供，管理端与学习端 MUST NOT 各自实现映射。

### Scenario：已知考试类型

- **WHEN** 传入 `ONLINE`、`SPECIAL_PRACTICE`、`BANK_PRACTICE` 或 `OFFLINE`
- **THEN** 函数 SHALL 分别返回「线上考试」「专项练习」「题库练习」「线下考试」。

### Scenario：未知或空类型

- **WHEN** 传入空值或未定义的枚举值
- **THEN** 函数 SHALL 返回通用文案「考试」
- **AND** MUST NOT 回显原始枚举码。
