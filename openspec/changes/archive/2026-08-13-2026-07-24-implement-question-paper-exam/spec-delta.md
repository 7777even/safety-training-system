# Spec Delta：题库 / 试卷 / 考试（M2）

## ADDED Requirements

### Requirement: 题目不可变版本

系统 MUST 在题目首次发布与后续内容变更时写入新的 `sts_question_version` 行并递增 `version_no`；已存在版本 MUST NOT
被就地修改题干/选项/答案。

### Requirement: 试卷发布固化题目

系统 MUST 支持 FIXED / RANDOM / CUSTOM 试卷；RANDOM 试卷在发布时 MUST 按抽题规则解析为不可变 `sts_paper_version_question`
快照。

### Requirement: 考试作答快照与幂等交卷

开考时 MUST 冻结题目与选项快照；交卷 MUST 对同一 attempt 幂等；超时交卷 MUST 走同一提交状态机。

### Requirement: 判分与成绩发布

客观题 MUST 自动判分；主观题 MUST 进入待人工批阅；批阅记录 MUST 只追加；成绩发布后考生方可按策略查看正式结果。

### Requirement: 课程考试类内容项

课程内容项类型 EXAM / QUESTION_BANK / PRACTICE MUST 可创建；完成态 MUST 可基于已发布考试成绩判定（最小钩子）。

## MODIFIED Requirements

无（新建域）。

## REMOVED Requirements

无。
