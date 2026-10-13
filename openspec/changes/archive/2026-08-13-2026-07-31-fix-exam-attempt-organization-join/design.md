# Design：修复考试作答查询机构表关联

## 根因

系统机构实体及 schema 使用 `sts_org_organization`。考试查询 XML 的 `selectReviewTaskPage` 和 `selectAttemptPage` 错写为
`sts_organization`，MySQL 报表不存在。

表名修复后真库继续暴露同一 Mapper 的审计分层错误：`sts_exam_reviewer`、`sts_exam_attempt_question`、
`sts_exam_review_record` 均为 Audit class B/C，不含逻辑删除列，但查询错误引用了这些表别名的 `deleted`。

## 方案

仅将两处：

`LEFT JOIN sts_organization org`

改为：

`LEFT JOIN sts_org_organization org`

同时移除仅针对上述无逻辑删除列审计表的无效 `deleted=0` 条件。`Exam`、`Attempt`、`Candidate`、`Answer` 等 class A
表的逻辑删除条件保持不变；关联字段、查询参数、返回字段和排序全部保持不变。

## 风险

低。属于表名纠错，不涉及契约、权限或数据迁移。
